package com.yoursay.posts.service;

import com.yoursay.posts.PostVotingConfigurationDto;
import com.yoursay.posts.PostVotingConfigurationService;
import com.yoursay.posts.VoteOptionDto;
import com.yoursay.posts.VotingType;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PostVotingConfigurationServiceImpl implements PostVotingConfigurationService {
    @Inject
    AgroalDataSource dataSource;

    @Override
    public Optional<PostVotingConfigurationDto> findByPostId(Long postId) {
        if (postId == null) return Optional.empty();
        String sql = """
                select p.voting_type, o.id, o.label, o.ordinal, o.semantic_key
                from post p
                left join post_vote_option o on o.post_id = p.id
                where p.id = ?
                order by o.ordinal
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, postId);
            try (ResultSet rows = statement.executeQuery()) {
                VotingType type = null;
                List<VoteOptionDto> options = new ArrayList<>();
                while (rows.next()) {
                    if (type == null) type = VotingType.valueOf(rows.getString("voting_type"));
                    Long optionId = rows.getObject("id", Long.class);
                    if (optionId != null) {
                        options.add(new VoteOptionDto(optionId, rows.getString("label"),
                                rows.getInt("ordinal"), rows.getString("semantic_key")));
                    }
                }
                return type == null
                        ? Optional.empty()
                        : Optional.of(new PostVotingConfigurationDto(postId, type, List.copyOf(options)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read post voting configuration", e);
        }
    }
}
