package com.yoursay.autopost;

import com.yoursay.autopost.dto.AutoPostEventDto;
import com.yoursay.autopost.dto.AutoPostRunDto;
import io.smallrye.mutiny.Multi;

import java.util.List;
import java.util.UUID;

public interface AutoPostService {

    AutoPostRunDto start(String administratorEmail);

    List<AutoPostRunDto> list(String administratorEmail);

    AutoPostRunDto get(UUID runId, String administratorEmail);

    AutoPostRunDto select(UUID runId, UUID candidateId, String administratorEmail);

    AutoPostRunDto approve(UUID runId, String administratorEmail);

    Multi<AutoPostEventDto> events(UUID runId, String administratorEmail);
}
