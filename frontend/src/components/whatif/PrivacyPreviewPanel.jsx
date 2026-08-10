const PII_LABELS = {
  RESIDENT_REGISTRATION_NUMBER: "주민등록번호",
  EMAIL: "이메일",
  PHONE_NUMBER: "전화번호",
  CARD_NUMBER: "카드번호",
  ACCOUNT_NUMBER: "계좌번호",
  API_KEY_OR_SECRET: "API Key 또는 Secret",
  LONG_NUMERIC_SEQUENCE: "긴 숫자열",
  CONTROL_CHARACTER: "제어문자",
};

export function PrivacyPreviewPanel({ preview, confirmed, onConfirm }) {
  if (!preview) {
    return (
      <section className="privacy-preview privacy-preview--waiting" aria-live="polite">
        <span aria-hidden="true">01</span><div><strong>Privacy Preview 대기</strong><p>자연어 실행 전에 외부 AI로 전달될 토큰화 문장을 반드시 확인합니다.</p></div>
      </section>
    );
  }

  if (preview.status === "BLOCKED") {
    return (
      <section className="privacy-preview privacy-preview--blocked" role="alert">
        <div className="privacy-preview-title"><span>BLOCKED</span><strong>외부 AI 호출이 차단됐습니다</strong></div>
        <p>탐지된 개인정보 유형만 표시하며 입력 원문은 오류에 다시 출력하지 않습니다.</p>
        <ul>{preview.blockedIdentifierTypes.map((type) => <li key={type}>{PII_LABELS[type] ?? type}</li>)}</ul>
      </section>
    );
  }

  if (preview.status !== "SAFE" || !preview.externalPayload) {
    return <section className="privacy-preview privacy-preview--blocked" role="alert"><strong>예상하지 못한 Preview 응답입니다</strong><p>외부 AI 호출은 허용되지 않았습니다.</p></section>;
  }

  return (
    <section className="privacy-preview privacy-preview--safe" aria-labelledby="privacy-preview-title">
      <div className="privacy-preview-title"><span>SAFE · {preview.privacyMode}</span><strong id="privacy-preview-title">AI 전달 내용</strong></div>
      <div className="tokenized-text"><span>TOKENIZED SCENARIO TEXT</span><p>{preview.externalPayload.sanitizedScenarioText}</p></div>
      <div className="privacy-preview-grid">
        <div><span>Reference Type</span><strong>{preview.referenceTypes.length ? preview.referenceTypes.join(" · ") : "없음"}</strong></div>
        <div><span>외부 전송 필드</span><strong>{preview.externalFieldNames.join(" · ")}</strong></div>
      </div>
      <p className="privacy-safe-notice">실제 금융값, Financial Profile, 사용자 식별자와 Reference 대응표는 외부로 전달하거나 화면에 표시하지 않습니다.</p>
      <label className="privacy-confirm"><input type="checkbox" checked={confirmed} onChange={(event) => onConfirm(event.target.checked)} /><span>토큰화된 전달 내용을 확인했습니다.</span></label>
    </section>
  );
}
