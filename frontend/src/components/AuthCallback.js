import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { setToken } from '../utils/auth';

function AuthCallback() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    const token = searchParams.get('token');
    const error = searchParams.get('error');

    if (token) {
      setToken(token);
      navigate('/', { replace: true });
    } else {
      navigate('/login?error=' + (error || 'unknown'), { replace: true });
    }
  }, [navigate, searchParams]);

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
      <p>Signing you in…</p>
    </div>
  );
}

export default AuthCallback;
