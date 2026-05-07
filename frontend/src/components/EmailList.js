import React, { useState, useEffect, useRef, useCallback } from 'react';
import ComposeEmail from './ComposeEmail';
import apiClient from '../utils/apiClient';

const POLL_INTERVAL_MS = 60000; // 60 seconds
const PAGE_SIZE = 50;
const SYSTEM_FOLDER_OPTIONS = ['INBOX', 'IMPORTANT', 'SOCIAL', 'PROMOTIONS', 'UPDATES', 'FORUMS', 'SPAM', 'TRASH', 'ARCHIVED', 'SENT', 'DRAFT'];

const buildFilterUrl = (accountId, currentFilter) => {
  let url = `/api/emails/account/${accountId}`;
  if (currentFilter === 'unread') url += '/unread';
  else if (currentFilter === 'urgent') url += '/importance/URGENT';
  else if (currentFilter === 'high') url += '/importance/HIGH';
  else if (currentFilter.startsWith('category:')) url += `/category/${currentFilter.slice('category:'.length)}`;
  else if (currentFilter.startsWith('folder:')) url += `/folder/${currentFilter.slice('folder:'.length)}`;
  return url;
};

const doesEmailMatchFilter = (email, currentFilter) => {
  if (currentFilter === 'all') return true;
  if (currentFilter === 'unread') return !email.isRead;
  if (currentFilter === 'urgent') return email.importance === 'URGENT';
  if (currentFilter === 'high') return email.importance === 'HIGH';
  if (currentFilter.startsWith('category:')) {
    return email.category === currentFilter.slice('category:'.length) && !email.folder;
  }
  if (currentFilter.startsWith('folder:')) return String(email.folder?.id || '') === currentFilter.slice('folder:'.length);
  return true;
};

const doesCustomFolderMoveMatchFilter = (currentFilter, targetFolderId) => {
  if (currentFilter === 'all') return true;
  if (currentFilter.startsWith('folder:')) {
    return currentFilter === `folder:${targetFolderId}`;
  }
  if (currentFilter.startsWith('category:')) {
    return false;
  }
  return true;
};

const PERMANENT_DELETE_CONFIRMATION = 'you are going to permanently delete the email(s), do you want to go ahead?';

const buildFilterOptions = (customFolders) => {
  const baseOptions = [
    { value: 'all', label: 'All' },
    { value: 'high', label: 'High Priority' },
    { value: 'unread', label: 'Unread' },
    { value: 'urgent', label: 'Urgent' },
    ...SYSTEM_FOLDER_OPTIONS.map((category) => ({
      value: `category:${category}`,
      label: category
    }))
  ];

  const dedupedByLabel = new Map();
  [...baseOptions, ...customFolders.map((folder) => ({
    value: `folder:${folder.id}`,
    label: folder.name
  }))].forEach((option) => {
    const normalizedLabel = option.label.trim().toUpperCase();
    if (!normalizedLabel || dedupedByLabel.has(normalizedLabel)) {
      return;
    }
    dedupedByLabel.set(normalizedLabel, option);
  });

  return Array.from(dedupedByLabel.values())
    .sort((left, right) => left.label.localeCompare(right.label, undefined, { sensitivity: 'base' }));
};

function EmailList({ accounts }) {
  const [emails, setEmails] = useState([]);
  const [customFolders, setCustomFolders] = useState([]);
  const [selectedAccount, setSelectedAccount] = useState(null);
  const [filter, setFilter] = useState('category:INBOX');
  const [loading, setLoading] = useState(false);
  const [selectedEmail, setSelectedEmail] = useState(null);
  const [emailLoading, setEmailLoading] = useState(false);
  const [composeMode, setComposeMode] = useState(null); // null | 'compose' | 'reply' | 'replyAll' | 'forward' | 'draft'
  const [selectedEmails, setSelectedEmails] = useState(new Set());
  const [actionFeedback, setActionFeedback] = useState('');
  const [newEmailCount, setNewEmailCount] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const searchContainerRef = useRef(null);

  // Stable refs so the polling interval doesn't go stale
  const selectedAccountRef = useRef(selectedAccount);
  const filterRef = useRef(filter);
  const emailsRef = useRef(emails);
  useEffect(() => { selectedAccountRef.current = selectedAccount; }, [selectedAccount]);
  useEffect(() => { filterRef.current = filter; }, [filter]);
  useEffect(() => { emailsRef.current = emails; }, [emails]);

  useEffect(() => {
    if (accounts.length > 0 && !selectedAccount) {
      setSelectedAccount(accounts[0].id);
    }
  }, [accounts, selectedAccount]);

  const currentPageEmails = emails;
  const currentPageEmailIds = currentPageEmails.map(email => email.id);
  const allCurrentPageSelected = currentPageEmailIds.length > 0
    && currentPageEmailIds.every(emailId => selectedEmails.has(emailId));
  const filterOptions = buildFilterOptions(customFolders);

  useEffect(() => {
    setCurrentPage(prevPage => Math.min(prevPage, totalPages));
  }, [totalPages]);

  // When the current page becomes empty but the backend still has emails (due to
  // deletions), automatically fetch the next available page.
  useEffect(() => {
    if (loading || emails.length > 0 || totalElements === 0 || !selectedAccount) return;
    const nextPage = currentPage > 1 ? currentPage - 1 : 1;
    setCurrentPage(nextPage);
    fetchEmails(selectedAccount, filter, false, nextPage);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [emails.length, loading, totalElements]);

  // Debounced keyword search
  useEffect(() => {
    const q = searchQuery.trim();
    if (!q || !selectedAccount) {
      setSearchResults([]);
      setSearchOpen(false);
      return;
    }
    setSearchLoading(true);
    const timer = setTimeout(async () => {
      try {
        const res = await apiClient.get(`/api/emails/account/${selectedAccount}/search?q=${encodeURIComponent(q)}&page=0&size=10`);
        if (res.ok) {
          const data = await res.json();
          setSearchResults(data.content || []);
          setSearchOpen(true);
        }
      } catch (_) {}
      finally { setSearchLoading(false); }
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery, selectedAccount]);

  // Close search dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (searchContainerRef.current && !searchContainerRef.current.contains(e.target)) {
        setSearchOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Auto-clear feedback
  useEffect(() => {
    if (actionFeedback) {
      const t = setTimeout(() => setActionFeedback(''), 3000);
      return () => clearTimeout(t);
    }
  }, [actionFeedback]);

  // Auto-clear new-email badge
  useEffect(() => {
    if (newEmailCount > 0) {
      const t = setTimeout(() => setNewEmailCount(0), 5000);
      return () => clearTimeout(t);
    }
  }, [newEmailCount]);

  // Background polling — silently checks for new emails without resetting loading state
  useEffect(() => {
    const poll = async () => {
      const accountId = selectedAccountRef.current;
      const currentFilter = filterRef.current;
      if (!accountId) return;
      try {
        // Sync from Gmail before checking for new arrivals
        await apiClient.post(`/api/accounts/${accountId}/sync`);

        const url = buildFilterUrl(accountId, currentFilter) + `?page=0&size=${PAGE_SIZE}`;

        const response = await apiClient.get(url);
        if (!response.ok) return;
        const data = await response.json();
        const fetched = data.content || [];

        setEmails(prev => {
          const prevIds = new Set(prev.map(e => e.id));
          const incoming = fetched.filter(e => !prevIds.has(e.id));
          if (incoming.length > 0) {
            setNewEmailCount(incoming.length);
            return [...incoming, ...prev];
          }
          return prev;
        });
      } catch (_) {
        // Silent — don't disturb the UI on poll failure
      }
    };

    const timer = setInterval(poll, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, []); // empty deps — uses refs to stay stable

  // Fetch local DB emails (no Gmail sync)
  const fetchEmails = useCallback(async (accountId = selectedAccount, currentFilter = filter, resetPage = true, page = 1) => {
    if (!accountId) return;
    setLoading(true);
    setNewEmailCount(0);
    try {
      const backendPage = resetPage ? 0 : page - 1;
      const url = buildFilterUrl(accountId, currentFilter) + `?page=${backendPage}&size=${PAGE_SIZE}`;

      const response = await apiClient.get(url);
      const data = await response.json();
      setEmails(data.content || []);
      setTotalPages(data.totalPages || 1);
      setTotalElements(data.totalElements || 0);
      setSelectedEmails(new Set());
      if (resetPage) setCurrentPage(1);
    } catch (error) {
      console.error('Error fetching emails:', error);
    } finally {
      setLoading(false);
    }
  }, [filter, selectedAccount]);

  const fetchCustomFolders = useCallback(async (accountId = selectedAccount) => {
    if (!accountId) return;
    try {
      const response = await apiClient.get(`/api/emails/account/${accountId}/folders`);
      if (!response.ok) {
        return;
      }
      const data = await response.json();
      setCustomFolders(data || []);
    } catch (error) {
      console.error('Error fetching folders:', error);
    }
  }, [selectedAccount]);

  useEffect(() => {
    if (selectedAccount) {
      fetchEmails();
      fetchCustomFolders();
    }
  }, [fetchCustomFolders, fetchEmails, selectedAccount]);

  // Sync from Gmail then reload local list
  const syncAndRefresh = async () => {
    if (!selectedAccount) return;
    setLoading(true);
    setNewEmailCount(0);
    try {
      await apiClient.post(`/api/accounts/${selectedAccount}/sync`);
    } catch (error) {
      console.error('Sync error:', error);
    }
    await fetchEmails(selectedAccount, filter, false);
  };

  const openEmail = async (email) => {
    setEmailLoading(true);
    try {
      const response = await apiClient.get(`/api/emails/${email.id}`);
      const data = await response.json();
      setSelectedEmail(data);
      if (!email.isRead) {
        await apiClient.put(`/api/emails/${email.id}/mark-read`);
        setEmails(prev => prev.map(e => e.id === email.id ? { ...e, isRead: true } : e));
      }
    } catch (error) {
      console.error('Error opening email:', error);
    } finally {
      setEmailLoading(false);
    }
  };

  const refreshEmailBody = async () => {
    if (!selectedEmail) return;
    setEmailLoading(true);
    try {
      const response = await apiClient.put(`/api/emails/${selectedEmail.id}/refresh-body`);
      if (response.ok) {
        const data = await response.json();
        setSelectedEmail(data);
      }
    } catch (error) {
      console.error('Error refreshing email body:', error);
    } finally {
      setEmailLoading(false);
    }
  };

  // --- Action handlers ---

  const trashEmail = async (emailId) => {
    try {
      const response = await apiClient.put(`/api/emails/${emailId}/trash`);
      if (response.ok) {
        setEmails(prev => prev.filter(e => e.id !== emailId));
        setSelectedEmails(prev => {
          const next = new Set(prev);
          next.delete(emailId);
          return next;
        });
        if (selectedEmail && selectedEmail.id === emailId) setSelectedEmail(null);
        setActionFeedback('Moved to Trash');
      }
    } catch (error) {
      console.error('Error trashing email:', error);
      setActionFeedback('Failed to delete');
    }
  };

  const archiveEmail = async (emailId) => {
    try {
      const response = await apiClient.put(`/api/emails/${emailId}/archive`);
      if (response.ok) {
        setEmails(prev => prev.filter(e => e.id !== emailId));
        setSelectedEmails(prev => {
          const next = new Set(prev);
          next.delete(emailId);
          return next;
        });
        if (selectedEmail && selectedEmail.id === emailId) setSelectedEmail(null);
        setActionFeedback('Archived');
      }
    } catch (error) {
      console.error('Error archiving email:', error);
    }
  };

  const moveEmail = async (emailId, category) => {
    try {
      const response = await apiClient.put(`/api/emails/${emailId}/move?category=${category}`);
      if (response.ok) {
        const updated = await response.json();
        setEmails(prev => prev.filter(e => e.id !== emailId || doesEmailMatchFilter(updated, filter))
          .map(e => e.id === emailId ? updated : e));
        setSelectedEmails(prev => {
          const next = new Set(prev);
          next.delete(emailId);
          return next;
        });
        if (selectedEmail && selectedEmail.id === emailId) {
          if (doesEmailMatchFilter(updated, filter)) {
            setSelectedEmail(updated);
          } else {
            setSelectedEmail(null);
          }
        }
        setActionFeedback(`Moved to ${category}`);
      }
    } catch (error) {
      console.error('Error moving email:', error);
    }
  };

  const moveEmailToCustomFolder = async (emailId, folderId) => {
    try {
      const response = await apiClient.put(`/api/emails/${emailId}/move-to-folder?folderId=${folderId}`);
      if (response.ok) {
        const updated = await response.json();
        const targetFolder = customFolders.find(folder => folder.id === folderId);
        const shouldRemainVisible = doesCustomFolderMoveMatchFilter(filter, folderId);
        const updatedEmail = {
          ...updated,
          folder: targetFolder ? { id: targetFolder.id, name: targetFolder.name } : updated.folder
        };
        setEmails(prev => prev.filter(e => e.id !== emailId || shouldRemainVisible)
          .map(e => e.id === emailId ? updatedEmail : e));
        if (selectedEmail && selectedEmail.id === emailId) {
          if (shouldRemainVisible) {
            setSelectedEmail(updatedEmail);
          } else {
            setSelectedEmail(null);
          }
        }
        setActionFeedback(`Moved to ${targetFolder?.name || 'folder'}`);
      }
    } catch (error) {
      console.error('Error moving email to folder:', error);
      setActionFeedback('Failed to move to folder');
    }
  };

  const createCustomFolder = async () => {
    if (!selectedAccount) {
      return;
    }

    const name = window.prompt('Enter a name for the new folder:');
    if (!name) {
      return;
    }

    try {
      const response = await apiClient.post(`/api/emails/account/${selectedAccount}/folders`, { name: name.trim() });

      if (!response.ok) {
        let msg = 'Failed to create folder';
        try {
          const data = await response.json();
          msg = data.message || msg;
        } catch (_) {}
        setActionFeedback(msg);
        return;
      }

      const folder = await response.json();
      setCustomFolders(prev => [...prev, folder].sort((left, right) => left.name.localeCompare(right.name)));
      setActionFeedback(`Created folder ${folder.name}`);

      if (selectedEmail) {
        await moveEmailToCustomFolder(selectedEmail.id, folder.id);
      }
    } catch (error) {
      console.error('Error creating folder:', error);
      setActionFeedback('Failed to create folder');
    }
  };

  const toggleStar = async (emailId, isStarred) => {
    try {
      const endpoint = isStarred ? 'unstar' : 'star';
      await apiClient.put(`/api/emails/${emailId}/${endpoint}`);
      setEmails(prev => prev.map(e => e.id === emailId ? { ...e, isStarred: !isStarred } : e));
      if (selectedEmail && selectedEmail.id === emailId) {
        setSelectedEmail(prev => ({ ...prev, isStarred: !isStarred }));
      }
    } catch (error) {
      console.error('Error toggling star:', error);
    }
  };

  const bulkTrash = async () => {
    for (const id of selectedEmails) {
      await trashEmail(id);
    }
    setSelectedEmails(new Set());
  };

  const bulkArchive = async () => {
    for (const id of selectedEmails) {
      await archiveEmail(id);
    }
    setSelectedEmails(new Set());
  };

  const discardDraft = async (draftId) => {
    try {
      const response = await apiClient.delete(`/api/emails/drafts/${draftId}`);
      if (response.ok) {
        setEmails(prev => prev.filter(e => e.id !== draftId));
        setSelectedEmails(prev => {
          const next = new Set(prev);
          next.delete(draftId);
          return next;
        });
        if (selectedEmail && selectedEmail.id === draftId) {
          setSelectedEmail(null);
        }
        setActionFeedback('Draft discarded');
      }
    } catch (error) {
      console.error('Error discarding draft:', error);
      setActionFeedback('Failed to discard draft');
    }
  };

  const permanentlyDeleteEmail = async (emailId) => {
    try {
      const response = await apiClient.delete(`/api/emails/${emailId}`);
      if (response.ok) {
        setEmails(prev => prev.filter(e => e.id !== emailId));
        setSelectedEmails(prev => {
          const next = new Set(prev);
          next.delete(emailId);
          return next;
        });
        if (selectedEmail && selectedEmail.id === emailId) {
          setSelectedEmail(null);
        }
        setActionFeedback('Email permanently deleted');
      }
    } catch (error) {
      console.error('Error permanently deleting email:', error);
      setActionFeedback('Failed to permanently delete');
    }
  };

  const toggleSelectAllOnPage = () => {
    setSelectedEmails(prev => {
      const next = new Set(prev);

      if (allCurrentPageSelected) {
        currentPageEmailIds.forEach(emailId => next.delete(emailId));
      } else {
        currentPageEmailIds.forEach(emailId => next.add(emailId));
      }

      return next;
    });
  };

  const goToPreviousPage = () => {
    const newPage = Math.max(currentPage - 1, 1);
    setCurrentPage(newPage);
    fetchEmails(selectedAccount, filter, false, newPage);
  };

  const goToNextPage = () => {
    const newPage = Math.min(currentPage + 1, totalPages);
    setCurrentPage(newPage);
    fetchEmails(selectedAccount, filter, false, newPage);
  };

  const toggleSelect = (emailId, e) => {
    e.stopPropagation();
    setSelectedEmails(prev => {
      const next = new Set(prev);
      if (next.has(emailId)) next.delete(emailId);
      else next.add(emailId);
      return next;
    });
  };

  const openReply = (replyAll = false) => {
    if (!selectedEmail) return;
    setComposeMode(replyAll ? 'replyAll' : 'reply');
  };

  const openForward = () => {
    if (!selectedEmail) return;
    setComposeMode('forward');
  };

  const openDraft = () => {
    if (!selectedEmail) return;
    setComposeMode('draft');
  };

  const getImportanceBadge = (importance) => {
    if (!importance) return null;
    const classes = {
      'URGENT': 'importance-badge importance-urgent',
      'HIGH': 'importance-badge importance-high',
      'NORMAL': 'importance-badge importance-normal',
      'LOW': 'importance-badge importance-low'
    };
    return <span className={classes[importance] || 'importance-badge'}>{importance}</span>;
  };

  const activeCategoryFilter = filter.startsWith('category:') ? filter.slice('category:'.length) : '';
  const isOutgoingFilter = activeCategoryFilter === 'SENT' || activeCategoryFilter === 'DRAFT';
  const isTrashFilter = activeCategoryFilter === 'TRASH';

  const handleDeleteEmail = async (emailId) => {
    if (isTrashFilter) {
      const confirmed = window.confirm(PERMANENT_DELETE_CONFIRMATION);
      if (!confirmed) {
        return;
      }
      await permanentlyDeleteEmail(emailId);
      return;
    }

    await trashEmail(emailId);
  };

  const bulkDeleteEmails = async () => {
    if (selectedEmails.size === 0) {
      return;
    }

    if (isTrashFilter) {
      const confirmed = window.confirm(PERMANENT_DELETE_CONFIRMATION);
      if (!confirmed) {
        return;
      }
      for (const id of selectedEmails) {
        await permanentlyDeleteEmail(id);
      }
      setSelectedEmails(new Set());
      return;
    }

    await bulkTrash();
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', gap: '1rem', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0 }}>Emails</h2>

        {/* Search bar */}
        <div ref={searchContainerRef} style={{ position: 'relative', flex: '1', maxWidth: '480px', minWidth: '220px' }}>
          <div style={{ display: 'flex', alignItems: 'center', border: '1px solid #ddd', borderRadius: '24px', padding: '6px 14px', background: '#f1f3f4', gap: '6px' }}>
            <span style={{ color: '#80868b', fontSize: '1rem' }}>🔍</span>
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={() => searchQuery.trim() && setSearchOpen(true)}
              placeholder="Search emails..."
              style={{ border: 'none', outline: 'none', background: 'transparent', flex: 1, fontSize: '0.95rem' }}
            />
            {searchLoading && <span style={{ fontSize: '0.75rem', color: '#80868b' }}>…</span>}
            {searchQuery && (
              <button
                onClick={() => { setSearchQuery(''); setSearchResults([]); setSearchOpen(false); }}
                style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#80868b', fontSize: '1rem', lineHeight: 1, padding: 0 }}
                title="Clear search"
              >✕</button>
            )}
          </div>

          {searchOpen && (
            <div style={{
              position: 'absolute', top: 'calc(100% + 6px)', left: 0, right: 0,
              background: '#fff', border: '1px solid #ddd', borderRadius: '8px',
              boxShadow: '0 4px 16px rgba(0,0,0,0.15)', zIndex: 200,
              maxHeight: '400px', overflowY: 'auto'
            }}>
              {searchResults.length === 0 ? (
                <div style={{ padding: '12px 16px', color: '#80868b', fontSize: '0.9rem' }}>No results found</div>
              ) : searchResults.map(email => (
                <div
                  key={email.id}
                  onClick={() => { setSearchOpen(false); setSearchQuery(''); openEmail(email); }}
                  style={{ padding: '10px 16px', cursor: 'pointer', borderBottom: '1px solid #f1f3f4' }}
                  onMouseEnter={e => e.currentTarget.style.background = '#f1f3f4'}
                  onMouseLeave={e => e.currentTarget.style.background = ''}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px' }}>
                    <span style={{ fontWeight: email.isRead ? 400 : 600, fontSize: '0.9rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
                      {email.subject || '(no subject)'}
                    </span>
                    <span style={{ fontSize: '0.78rem', color: '#80868b', whiteSpace: 'nowrap' }}>
                      {email.receivedDate ? new Date(email.receivedDate).toLocaleDateString() : ''}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.8rem', color: '#5f6368', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {email.fromAddress}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          {totalElements > 0 && (
            <span style={{
              fontSize: '0.82rem',
              color: '#fff',
              background: '#1a73e8',
              padding: '2px 10px',
              borderRadius: '12px',
              fontWeight: 500,
              whiteSpace: 'nowrap'
            }}>
              {totalElements} email{totalElements !== 1 ? 's' : ''}
            </span>
          )}
          <button className="btn btn-primary" onClick={() => setComposeMode('compose')}
            style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.95rem' }}>
            ✏️ Compose
          </button>
        </div>
      </div>

      {actionFeedback && (
        <div className="action-feedback">{actionFeedback}</div>
      )}

      {newEmailCount > 0 && (
        <div className="action-feedback" style={{background: '#1a73e8', cursor: 'pointer'}}
          onClick={fetchEmails}>
          📬 {newEmailCount} new email{newEmailCount > 1 ? 's' : ''} arrived — click to refresh
        </div>
      )}

      <div style={{display: 'flex', gap: '1rem', alignItems: 'flex-start'}}>
        {/* Email List Panel */}
        <div className="card" style={{flex: selectedEmail ? '0 0 40%' : '1', minWidth: 0}}>
        <div style={{display: 'flex', gap: '1rem', marginBottom: '1rem', flexWrap: 'wrap', alignItems: 'center'}}>
          <div>
            <label htmlFor="account-select">Account: </label>
            <select
              id="account-select"
              value={selectedAccount || ''}
              onChange={(e) => setSelectedAccount(Number(e.target.value))}
              style={{padding: '0.5rem', borderRadius: '4px', border: '1px solid #ddd'}}
            >
              {accounts.map(account => (
                <option key={account.id} value={account.id}>
                  {account.emailAddress}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="filter-select">Filter: </label>
            <select
              id="filter-select"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              style={{padding: '0.5rem', borderRadius: '4px', border: '1px solid #ddd'}}
            >
              {filterOptions.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </div>

          <button onClick={syncAndRefresh} className="btn btn-primary">
            🔄 Refresh
          </button>

          {selectedEmails.size > 0 && (
            <div className="bulk-actions">
              <span style={{ fontSize: '0.85rem', color: '#5f6368' }}>{selectedEmails.size} selected</span>
              <button className="btn-icon" title={isTrashFilter ? 'Permanently delete selected' : 'Delete selected'} onClick={bulkDeleteEmails}>🗑️</button>
              <button className="btn-icon" title="Archive selected" onClick={bulkArchive}>📥</button>
            </div>
          )}
        </div>

        {loading ? (
          <p>Loading emails...</p>
        ) : emails.length === 0 ? (
          <p>No emails found.</p>
        ) : (
          <>
            <div className="email-list-toolbar">
              <button className="btn btn-secondary" onClick={toggleSelectAllOnPage}>
                {allCurrentPageSelected ? 'Clear page selection' : 'Select all on page'}
              </button>
              <span className="email-page-summary">
                Showing {(currentPage - 1) * PAGE_SIZE + 1}-{(currentPage - 1) * PAGE_SIZE + emails.length} of {totalElements}
              </span>
            </div>

            <ul className="email-list">
            {currentPageEmails.map(email => (
              <li
                key={email.id}
                className={`email-item ${!email.isRead ? 'unread' : ''} ${email.importance ? email.importance.toLowerCase() : ''}`}
                onClick={() => openEmail(email)}
                style={{cursor: 'pointer', background: selectedEmail && selectedEmail.id === email.id ? '#eaf4fb' : ''}}
              >
                <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                  <input
                    type="checkbox"
                    checked={selectedEmails.has(email.id)}
                    onChange={(e) => toggleSelect(email.id, e)}
                    onClick={(e) => e.stopPropagation()}
                    style={{ cursor: 'pointer', accentColor: '#1a73e8' }}
                  />
                  <span
                    className="star-btn"
                    onClick={(e) => { e.stopPropagation(); toggleStar(email.id, email.isStarred); }}
                    title={email.isStarred ? 'Unstar' : 'Star'}
                  >
                    {email.isStarred ? '⭐' : '☆'}
                  </span>
                  <div style={{flex: 1, minWidth: 0}}>
                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                      <div>
                        <strong>{isOutgoingFilter ? (email.toAddresses || '(No recipient)') : email.fromAddress}</strong>
                        {isOutgoingFilter && <span style={{marginLeft: '6px', fontSize: '0.8rem', color: '#5f6368'}}>To</span>}
                        {getImportanceBadge(email.importance)}
                        {email.isPhishing && <span style={{color: 'red', marginLeft: '0.5rem'}}>⚠️ PHISHING</span>}
                        {email.isSpam && <span style={{color: 'orange', marginLeft: '0.5rem'}}>🗑️ SPAM</span>}
                        {email.category === 'DRAFT' && <span style={{marginLeft: '8px', fontSize: '0.8rem', color: '#f29900'}}>Draft</span>}
                      </div>
                      <div style={{fontSize: '0.9rem', color: '#7f8c8d', display: 'flex', alignItems: 'center', gap: '8px'}}>
                        {new Date(email.receivedDate).toLocaleString()}
                        <span className="email-item-actions" onClick={e => e.stopPropagation()}>
                          <button className="btn-icon-sm" title="Archive" onClick={() => archiveEmail(email.id)}>📥</button>
                          <button className="btn-icon-sm" title={isTrashFilter ? 'Permanently delete' : 'Delete'} onClick={() => handleDeleteEmail(email.id)}>🗑️</button>
                        </span>
                      </div>
                    </div>
                    <div style={{marginTop: '0.5rem', fontWeight: email.isRead ? 'normal' : 'bold'}}>
                      {email.subject}
                    </div>
                    {email.dueDate && (
                      <div style={{marginTop: '0.5rem', color: '#e74c3c', fontSize: '0.9rem'}}>
                        📅 Due: {new Date(email.dueDate).toLocaleDateString()}
                      </div>
                    )}
                  </div>
                </div>
              </li>
            ))}
            </ul>

            <div className="pagination-controls">
              <button className="btn btn-secondary" onClick={goToPreviousPage} disabled={currentPage === 1}>
                Previous
              </button>
              <span className="pagination-status">Page {currentPage} of {totalPages}</span>
              <button className="btn btn-secondary" onClick={goToNextPage} disabled={currentPage === totalPages}>
                Next
              </button>
            </div>
          </>
        )}
        </div>

        {/* Email Detail Panel */}
        {selectedEmail && (
          <div style={{
            flex: '1',
            minWidth: 0,
            background: 'white',
            borderRadius: '8px',
            boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            height: 'calc(100vh - 140px)',
          }}>
            {/* Toolbar */}
            <div className="email-toolbar">
              <div className="email-toolbar-left">
                <button className="btn-icon" title="Archive" onClick={() => archiveEmail(selectedEmail.id)}>📥</button>
                <button className="btn-icon" title={isTrashFilter ? 'Permanently delete' : 'Delete'} onClick={() => handleDeleteEmail(selectedEmail.id)}>🗑️</button>
                <span className="toolbar-divider" />
                <div className="move-dropdown">
                  <button className="btn-icon" title="Move to...">📁</button>
                  <div className="move-dropdown-content">
                    {SYSTEM_FOLDER_OPTIONS.map(cat => (
                      <button key={cat} onClick={() => moveEmail(selectedEmail.id, cat)}>{cat}</button>
                    ))}
                    {customFolders.length > 0 && <div className="move-dropdown-divider" />}
                    {customFolders.map(folder => (
                      <button key={folder.id} onClick={() => moveEmailToCustomFolder(selectedEmail.id, folder.id)}>
                        {folder.name}
                      </button>
                    ))}
                    <div className="move-dropdown-divider" />
                    <button onClick={createCustomFolder}>+ New folder...</button>
                  </div>
                </div>
                <span className="toolbar-divider" />
                <button className="btn-icon" title={selectedEmail.isStarred ? 'Unstar' : 'Star'}
                  onClick={() => toggleStar(selectedEmail.id, selectedEmail.isStarred)}>
                  {selectedEmail.isStarred ? '⭐' : '☆'}
                </button>
                <button className="btn-icon" title={selectedEmail.isRead ? 'Mark unread' : 'Mark read'}
                  onClick={async () => {
                    const endpoint = selectedEmail.isRead ? 'mark-unread' : 'mark-read';
                    await apiClient.put(`/api/emails/${selectedEmail.id}/${endpoint}`);
                    setSelectedEmail(prev => ({ ...prev, isRead: !prev.isRead }));
                    setEmails(prev => prev.map(e => e.id === selectedEmail.id ? { ...e, isRead: !selectedEmail.isRead } : e));
                  }}>
                  {selectedEmail.isRead ? '✉️' : '📭'}
                </button>
              </div>
              <button
                onClick={() => setSelectedEmail(null)}
                title="Close"
                className="btn-icon"
              >
                ✕
              </button>
            </div>

            {/* Gmail-style header bar */}
            <div style={{
              padding: '16px 24px',
              borderBottom: '1px solid #e0e0e0',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'flex-start',
              background: 'white',
            }}>
              <div style={{flex: 1, minWidth: 0}}>
                <div style={{fontSize: '1.4rem', fontWeight: 400, color: '#202124', marginBottom: '12px', wordBreak: 'break-word'}}>
                  {selectedEmail.subject}
                  {selectedEmail.importance === 'URGENT' && <span style={{marginLeft: '8px', color: '#d93025', fontSize: '0.9rem', fontWeight: 500}}>● URGENT</span>}
                  {selectedEmail.importance === 'HIGH' && <span style={{marginLeft: '8px', color: '#f29900', fontSize: '0.9rem', fontWeight: 500}}>● HIGH</span>}
                  {selectedEmail.isPhishing && <span style={{marginLeft: '8px', color: '#d93025', fontSize: '0.85rem', background: '#fce8e6', padding: '2px 8px', borderRadius: '4px'}}>⚠️ Phishing</span>}
                  {selectedEmail.isSpam && <span style={{marginLeft: '8px', color: '#80868b', fontSize: '0.85rem', background: '#f1f3f4', padding: '2px 8px', borderRadius: '4px'}}>🗑️ Spam</span>}
                  {selectedEmail.folder?.name && <span style={{marginLeft: '8px', color: '#1967d2', fontSize: '0.85rem', background: '#e8f0fe', padding: '2px 8px', borderRadius: '4px'}}>Folder: {selectedEmail.folder.name}</span>}
                </div>
                {/* Sender row */}
                <div style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
                  <div style={{
                    width: '40px', height: '40px', borderRadius: '50%',
                    background: '#1a73e8', color: 'white',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '1rem', fontWeight: 500, flexShrink: 0,
                  }}>
                    {(selectedEmail.fromName || selectedEmail.fromAddress || '?')[0].toUpperCase()}
                  </div>
                  <div style={{flex: 1, minWidth: 0}}>
                    <div style={{display: 'flex', alignItems: 'baseline', gap: '8px', flexWrap: 'wrap'}}>
                      <span style={{fontWeight: 500, color: '#202124', fontSize: '0.95rem'}}>
                        {selectedEmail.fromName || selectedEmail.fromAddress}
                      </span>
                      {selectedEmail.fromName && (
                        <span style={{color: '#5f6368', fontSize: '0.85rem'}}>
                          &lt;{selectedEmail.fromAddress}&gt;
                        </span>
                      )}
                    </div>
                    <div style={{color: '#5f6368', fontSize: '0.82rem', marginTop: '2px'}}>
                      {selectedEmail.toAddresses && <span>to {selectedEmail.toAddresses} · </span>}
                      {new Date(selectedEmail.receivedDate).toLocaleString()}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Email body rendered in iframe */}
            {emailLoading ? (
              <div style={{padding: '24px', color: '#5f6368'}}>Loading...</div>
            ) : (!selectedEmail.bodyHtml && !selectedEmail.bodyPlainText) ? (
              <div style={{
                flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center',
                justifyContent: 'center', padding: '40px', color: '#5f6368', gap: '16px',
              }}>
                <div style={{fontSize: '2rem'}}>📷</div>
                <div style={{fontWeight: 500}}>No text content — this email may contain only images or attachments.</div>
                <button
                  onClick={refreshEmailBody}
                  style={{
                    background: '#1a73e8', color: '#fff', border: 'none', borderRadius: '4px',
                    padding: '8px 20px', cursor: 'pointer', fontSize: '0.9rem',
                  }}
                >
                  ↺ Reload from Gmail
                </button>
              </div>
            ) : (
              <iframe
                title="email-body"
                sandbox="allow-scripts allow-popups allow-popups-to-escape-sandbox"
                srcDoc={(() => {
                  const baseTag = `<base target="_blank" rel="noopener noreferrer">`;
                  const html = selectedEmail.bodyHtml
                    || (selectedEmail.bodyPlainText && (
                        selectedEmail.bodyPlainText.trimStart().startsWith('<') ||
                        selectedEmail.bodyPlainText.includes('<html') ||
                        selectedEmail.bodyPlainText.includes('<div') ||
                        selectedEmail.bodyPlainText.includes('<table')
                       ) ? selectedEmail.bodyPlainText : null);
                  if (html) {
                    return `<!DOCTYPE html><html><head><meta charset="utf-8">${baseTag}<style>body{margin:0;padding:16px 24px;font-family:Arial,sans-serif;font-size:14px;color:#202124;word-break:break-word;}img{max-width:100%;height:auto;}a{color:#1a73e8;cursor:pointer;}</style></head><body>${html}</body></html>`;
                  }
                  const text = (selectedEmail.bodyPlainText || '')
                    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
                    .replace(/https?:\/\/[^\s]+/g, url => `<a href="${url}" target="_blank" rel="noopener noreferrer">${url}</a>`);
                  return `<!DOCTYPE html><html><head><meta charset="utf-8">${baseTag}<style>body{margin:0;padding:16px 24px;font-family:Arial,sans-serif;font-size:14px;color:#202124;white-space:pre-wrap;word-break:break-word;line-height:1.6;}a{color:#1a73e8;}</style></head><body>${text}</body></html>`;
                })()}
                style={{
                  flex: 1,
                  width: '100%',
                  border: 'none',
                  minHeight: 0,
                }}
              />
            )}

            {/* Reply / Forward / Open action bar */}
            <div className="email-action-bar">
              {selectedEmail.category === 'DRAFT' ? (
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button className="btn-action" onClick={openDraft}>
                    ✏️ Resume Draft
                  </button>
                  <button className="btn-action-secondary" onClick={() => discardDraft(selectedEmail.id)}>
                    🗑️ Discard Draft
                  </button>
                </div>
              ) : (
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button className="btn-action" onClick={() => openReply(false)}>
                    ↩️ Reply
                  </button>
                  <button className="btn-action" onClick={() => openReply(true)}>
                    ↩️↩️ Reply All
                  </button>
                  <button className="btn-action" onClick={openForward}>
                    ↪️ Forward
                  </button>
                </div>
              )}
              <button
                onClick={() => {
                  const html = selectedEmail.bodyHtml || selectedEmail.bodyPlainText || '(No content)';
                  const fullHtml = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${selectedEmail.subject || 'Email'}</title><style>body{margin:24px auto;max-width:860px;font-family:Arial,sans-serif;font-size:14px;color:#202124;word-break:break-word;}img{max-width:100%;height:auto;}a{color:#1a73e8;}.header{margin-bottom:24px;padding-bottom:16px;border-bottom:1px solid #e0e0e0;}.header h1{font-size:1.4rem;font-weight:400;margin:0 0 12px 0;}.meta{color:#5f6368;font-size:0.85rem;line-height:1.8;}</style></head><body><div class="header"><h1>${selectedEmail.subject || ''}</h1><div class="meta"><b>From:</b> ${selectedEmail.fromAddress || ''}<br><b>To:</b> ${selectedEmail.toAddresses || ''}<br><b>Date:</b> ${selectedEmail.receivedDate ? new Date(selectedEmail.receivedDate).toLocaleString() : ''}</div></div>${html}</body></html>`;
                  const blob = new Blob([fullHtml], {type: 'text/html'});
                  const url = URL.createObjectURL(blob);
                  window.open(url, '_blank');
                  setTimeout(() => URL.revokeObjectURL(url), 60000);
                }}
                className="btn-action-secondary"
              >
                ↗ Open in browser tab
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Compose / Reply / Forward modal */}
      {composeMode && (
        <ComposeEmail
          accounts={accounts}
          onClose={() => setComposeMode(null)}
          onSent={() => {
            setActionFeedback('Message sent!');
            if (composeMode === 'draft') {
              setSelectedEmail(null);
            }
            fetchEmails();
          }}
          onDraftSaved={() => {
            setActionFeedback('Draft saved');
            setSelectedEmail(null);
            fetchEmails();
          }}
          onDraftDiscarded={() => {
            setActionFeedback('Draft discarded');
            setSelectedEmail(null);
            fetchEmails();
          }}
          replyTo={composeMode === 'reply' || composeMode === 'replyAll' ? {
            emailId: selectedEmail.id,
            accountId: selectedAccount,
            to: selectedEmail.fromAddress,
            cc: composeMode === 'replyAll' ? selectedEmail.ccAddresses || '' : '',
            originalToAddresses: selectedEmail.toAddresses || '',
            originalCcAddresses: selectedEmail.ccAddresses || '',
            subject: selectedEmail.subject ? (selectedEmail.subject.startsWith('Re:') ? selectedEmail.subject : `Re: ${selectedEmail.subject}`) : 'Re:',
            fromName: selectedEmail.fromName,
            date: selectedEmail.receivedDate ? new Date(selectedEmail.receivedDate).toLocaleString() : '',
            originalText: selectedEmail.bodyPlainText || '',
            replyAll: composeMode === 'replyAll'
          } : null}
          forwardEmail={composeMode === 'forward' ? {
            emailId: selectedEmail.id,
            accountId: selectedAccount,
            subject: selectedEmail.subject || '',
            fromAddress: selectedEmail.fromAddress || '',
            toAddresses: selectedEmail.toAddresses || '',
            receivedDate: selectedEmail.receivedDate,
            bodyPlainText: selectedEmail.bodyPlainText || '',
            bodyHtml: selectedEmail.bodyHtml || ''
          } : null}
          draftEmail={composeMode === 'draft' ? {
            id: selectedEmail.id,
            accountId: selectedAccount,
            toAddresses: selectedEmail.toAddresses || '',
            ccAddresses: selectedEmail.ccAddresses || '',
            subject: selectedEmail.subject || '',
            bodyPlainText: selectedEmail.bodyPlainText || ''
          } : null}
        />
      )}
    </div>
  );
}

export default EmailList;
