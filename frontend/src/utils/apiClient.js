const apiClient_headers = (extra = {}) => ({
  'Content-Type': 'application/json',
  ...extra,
});

const apiClient = {
  get: (url) =>
    fetch(url, { headers: apiClient_headers() }),

  post: (url, body) =>
    fetch(url, {
      method: 'POST',
      headers: apiClient_headers(),
      body: JSON.stringify(body),
    }),

  put: (url, body) =>
    fetch(url, {
      method: 'PUT',
      headers: apiClient_headers(),
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),

  delete: (url) =>
    fetch(url, { method: 'DELETE', headers: apiClient_headers() }),
};

export default apiClient;
