package com.yoursay.autopost;

import com.yoursay.autopost.dto.AutoPostEventDto;
import com.yoursay.autopost.dto.AutoPostRunDto;
import io.smallrye.mutiny.Multi;

import java.util.List;
import java.util.UUID;

public interface AutoPostService {

    AutoPostRunDto startDiscoveryRun(String administratorEmail);

    List<AutoPostRunDto> listRecentRuns(String administratorEmail);

    AutoPostRunDto getRun(UUID runId, String administratorEmail);

    AutoPostRunDto selectCandidateForDrafting(
            UUID runId,
            UUID candidateId,
            String administratorEmail
    );

    AutoPostRunDto approveAndPublishDraft(UUID runId, String administratorEmail);

    Multi<AutoPostEventDto> streamRunEvents(UUID runId, String administratorEmail);
}
