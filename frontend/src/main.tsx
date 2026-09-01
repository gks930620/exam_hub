import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth';

// Halo 디자인 — 토큰을 가장 먼저. 순서가 바뀌면 변수 없이 컴포넌트가 로드된다.
// (설계/디자인_Halo/halo-design-kit/HALO-디자인-가이드.md §3-1)
import './styles/halo-tokens.css';
import './styles/halo-components.css';
import './styles/app.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
