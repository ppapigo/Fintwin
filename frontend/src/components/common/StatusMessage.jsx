export function StatusMessage({ title, description, actionLabel, onAction, tone = "neutral" }) {
  return (
    <section className={`status-panel status-panel--${tone}`} role={tone === "error" ? "alert" : "status"}>
      <p className="eyebrow">FinTwin</p>
      <h1>{title}</h1>
      {description && <p>{description}</p>}
      {actionLabel && onAction && (
        <button className="button button--primary" type="button" onClick={onAction}>
          {actionLabel}
        </button>
      )}
    </section>
  );
}
