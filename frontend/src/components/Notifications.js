import React from 'react';
import apiClient from '../utils/apiClient';

function Notifications({ notifications, onRefresh }) {
  const markAsRead = async (id) => {
    try {
      await apiClient.put(`/api/notifications/${id}/mark-read`);
      onRefresh();
    } catch (error) {
      console.error('Error marking notification as read:', error);
    }
  };

  const markAllAsRead = async () => {
    try {
      await apiClient.put('/api/notifications/mark-all-read');
      onRefresh();
    } catch (error) {
      console.error('Error marking all notifications as read:', error);
    }
  };

  const getNotificationIcon = (type) => {
    const icons = {
      'URGENT_EMAIL': '⚡',
      'DEADLINE_APPROACHING': '📅',
      'SPAM_DETECTED': '🗑️',
      'PHISHING_DETECTED': '⚠️',
      'ACCOUNT_SYNC_FAILED': '❌',
      'DAILY_SUMMARY': '📊'
    };
    return icons[type] || '📧';
  };

  const getNotificationColor = (type) => {
    const colors = {
      'URGENT_EMAIL': '#e74c3c',
      'DEADLINE_APPROACHING': '#f39c12',
      'SPAM_DETECTED': '#95a5a6',
      'PHISHING_DETECTED': '#c0392b',
      'ACCOUNT_SYNC_FAILED': '#e67e22',
      'DAILY_SUMMARY': '#3498db'
    };
    return colors[type] || '#2c3e50';
  };

  return (
    <div>
      <h2>Notifications</h2>

      <div className="card">
        <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem'}}>
          <h3>Recent Notifications ({notifications.length})</h3>
          {notifications.length > 0 && (
            <button onClick={markAllAsRead} className="btn btn-primary">
              Mark All as Read
            </button>
          )}
        </div>

        {notifications.length === 0 ? (
          <p style={{textAlign: 'center', color: '#7f8c8d', padding: '2rem'}}>
            No notifications
          </p>
        ) : (
          <div>
            {notifications.map(notification => (
              <div 
                key={notification.id}
                style={{
                  padding: '1rem',
                  marginBottom: '1rem',
                  borderRadius: '4px',
                  background: notification.isRead ? '#f9f9f9' : '#e8f4fd',
                  borderLeft: `4px solid ${getNotificationColor(notification.type)}`,
                  cursor: 'pointer'
                }}
                onClick={() => !notification.isRead && markAsRead(notification.id)}
              >
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start'}}>
                  <div style={{flex: 1}}>
                    <div style={{display: 'flex', alignItems: 'center', marginBottom: '0.5rem'}}>
                      <span style={{fontSize: '1.5rem', marginRight: '0.5rem'}}>
                        {getNotificationIcon(notification.type)}
                      </span>
                      <strong style={{color: getNotificationColor(notification.type)}}>
                        {notification.type.replace(/_/g, ' ')}
                      </strong>
                    </div>
                    <p style={{margin: '0.5rem 0', color: '#2c3e50'}}>
                      {notification.message}
                    </p>
                    <div style={{fontSize: '0.85rem', color: '#7f8c8d'}}>
                      {new Date(notification.sentAt).toLocaleString()}
                    </div>
                  </div>
                  {!notification.isRead && (
                    <button 
                      onClick={(e) => {
                        e.stopPropagation();
                        markAsRead(notification.id);
                      }}
                      className="btn btn-primary"
                      style={{padding: '0.25rem 0.75rem', fontSize: '0.85rem'}}
                    >
                      Mark Read
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default Notifications;
