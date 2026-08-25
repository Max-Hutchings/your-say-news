import { AutoPostDesk, useAutoPosts } from "../../features/auto-post";

export function AutoPostPage() {
  const autoPost = useAutoPosts();
  return <AutoPostDesk
    runs={autoPost.runs}
    activeRun={autoPost.activeRun}
    viewedRun={autoPost.viewedRun}
    error={autoPost.error}
    creating={autoPost.creating}
    selectingCandidateId={autoPost.selectingCandidateId}
    approving={autoPost.approving}
    retryingRunId={autoPost.retryingRunId}
    publishedPostId={autoPost.publishedPostId}
    loadingRunId={autoPost.loadingRunId}
    onCreate={autoPost.create}
    onSelect={autoPost.select}
    onApprove={autoPost.approve}
    onRetry={autoPost.retry}
    onViewRun={autoPost.viewRun}
    onReload={autoPost.load}
  />;
}
