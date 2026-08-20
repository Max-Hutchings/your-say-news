import { AutoPostDesk, useAutoPosts } from "../../features/auto-post";

export function AutoPostPage() {
  const autoPost = useAutoPosts();
  return <AutoPostDesk
    runs={autoPost.runs}
    activeRun={autoPost.activeRun}
    error={autoPost.error}
    creating={autoPost.creating}
    selectingCandidateId={autoPost.selectingCandidateId}
    approving={autoPost.approving}
    onCreate={autoPost.create}
    onSelect={autoPost.select}
    onApprove={autoPost.approve}
    onReload={autoPost.load}
  />;
}
