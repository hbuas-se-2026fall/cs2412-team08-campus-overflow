export function SkeletonList({ rows = 6 }: { rows?: number }) {
  return (
    <div className="skeleton-list" aria-busy="true" aria-label="正在加载">
      {Array.from({ length: rows }).map((_, index) => (
        <div className="skeleton-row" key={index}>
          <div className="sk sk-stats" />
          <div className="sk-main">
            <div className="sk sk-title" />
            <div className="sk sk-line" />
            <div className="sk sk-line short" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function LoadingBlock({ text = '加载中…' }: { text?: string }) {
  return <p className="muted loading-block">{text}</p>;
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  if (!message) return null;
  return (
    <div className="errorbox" role="alert">
      <span>{message}</span>
      {onRetry && <button onClick={onRetry}>重试</button>}
    </div>
  );
}
