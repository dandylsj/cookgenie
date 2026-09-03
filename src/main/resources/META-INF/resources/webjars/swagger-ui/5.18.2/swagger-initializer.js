window.onload = function() {
  // 스키마 필드의 한글 설명을 타입 옆 같은 줄에 표시 (기본은 다음 줄에 별도 표시됨)
  var style = document.createElement('style');
  style.textContent = `
    .property-row .renderedMarkdown { display: inline !important; }
    .property-row .renderedMarkdown p { display: inline !important; margin: 0 !important; }
    .property-row .renderedMarkdown::before { content: " — "; color: #888; }
    .property-row .property.primitive br { display: none !important; }
    .property-row .property.primitive { margin-left: 6px; }
  `;
  document.head.appendChild(style);

  window.ui = SwaggerUIBundle({
    url: "/v3/api-docs",
    dom_id: '#swagger-ui',
    deepLinking: true,
    persistAuthorization: true,
    defaultModelExpandDepth: 3,
    defaultModelsExpandDepth: 0,
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl
    ],
    layout: "StandaloneLayout",

    responseInterceptor: function(response) {
      // 회원가입/로그인 응답에서 accessToken 자동 추출 후 Authorize 등록
      var isAuthEndpoint = response.url && (response.url.includes('/auth/login') || response.url.includes('/auth/sign'));
      if (isAuthEndpoint && response.status === 200) {
        try {
          const body = response.body;
          const token = body?.data?.accessToken;
          if (token) {
            window.ui.preauthorizeApiKey('jwtAuth', token);
            console.log('✅ AccessToken 자동 등록 완료');
          }
        } catch (e) {
          console.warn('⚠️ Token 자동 등록 실패:', e);
        }
      }
      return response;
    }
  });
};
