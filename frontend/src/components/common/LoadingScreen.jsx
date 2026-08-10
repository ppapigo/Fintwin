export function LoadingScreen({ label = "불러오는 중입니다." }) {
  return (
    <div className="centered-state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <p>{label}</p>
    </div>
  );
}
