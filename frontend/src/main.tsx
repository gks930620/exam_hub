import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth';

// Lets 디자인 킷 — 폴더는 킷 문서가 정한 대로 프로젝트 루트(exam_hub/design_kits_lets)에 하나만 둔다.
// 로드 순서는 킷 사용법 §1 그대로 (base → components → tokens → extras). 앱 고유 스타일은 맨 뒤.
// 이전 킷(Halo)으로의 복구 경로는 태그 design-halo — styles/halo-*.css 는 그래서 남겨 둔다.
import '../../design_kits_lets/base.css';
import '../../design_kits_lets/components.css';
import '../../design_kits_lets/tokens.css';
import '../../design_kits_lets/extras.css';
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
