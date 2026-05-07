import { getToken, clearToken } from './auth';

const handleResponse = async (res) => {
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    return Promise.reject(new Error('Unauthenticated'));
  }
  return res;
};

const authHeaders = (extra = {}) => {
  const token = getToken();
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...extra,
  };
};

const apiClient = {
  get: (url) =>
    fetch(url, { headers: authHeaders() }).then(handleResponse),

  post: (url, body) =>
    fetch(url, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(body),
    }).then(handleResponse),

  put: (url, body) =>
    fetch(url, {
      method: 'PUT',
      headers: authHeaders(),
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }).then(handleResponse),

  delete: (url) =>
    fetch(url, { method: 'DELETE', headers: authHeaders() }).then(handleResponse),
};

export default apiClient;
