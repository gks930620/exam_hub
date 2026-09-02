import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth';

// Lets 디자인 킷 — 폴더는 킷 문서가 정한 대로 프로젝트 루트(exam_hub/design_kits_lets)에 하나만 둔다.
// 로드 순서는 킷 사용법 §1 그대로 (base → components → tokens → extras).
// Halo 로 되돌리려면: 이 블록을 halo-tokens/halo-components 로 바꾸고
// git checkout design-halo -- frontend/src/styles/app.css  (태그 design-halo = Halo 마지막 상태)
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
