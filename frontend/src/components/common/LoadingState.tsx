export default function LoadingState({ message }: { message: string }) {
  return <div className="loading-state" role="status">
    <span>{message}</span>
    <div className="loading-state-lines" aria-hidden="true"><span/><span/></div>
  </div>;
}
