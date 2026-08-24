package com.yoursay.topics.service;

import com.yoursay.platform.observability.ApiException;
import com.yoursay.topics.dto.CreateTopicRequest;
import com.yoursay.topics.dto.TopicTagDto;
import com.yoursay.topics.model.PostTopicTagRepository;
import com.yoursay.topics.model.Topic;
import com.yoursay.topics.model.TopicRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The validation the topics domain owns. These are the branches that decide whether a post is
 * published with the topics its author picked, so each one asserts the specific error code the API
 * returns rather than merely that something failed.
 */
class TopicServiceImplTest {

    private static final Topic HOUSING = new Topic("housing", "Housing", "Society", 6);
    private static final Topic HEALTH = new Topic("health", "Health", "Society", 3);
    private static final Topic CRIME = new Topic("crime", "Crime", "Society", 15);
    private static final Topic POLITICS = new Topic("politics", "Politics", "Politics & government", 1);

    @Test
    void attachesEveryValidTopicAndReportsThemBack() {
        StubTopicRepository topics = new StubTopicRepository(List.of(HOUSING, HEALTH));
        StubPostTopicTagRepository assignments = new StubPostTopicTagRepository();
        TopicServiceImpl service = serviceWith(topics, assignments);

        List<TopicTagDto> result = service.assignCreatorTags(88L, List.of("housing", "health"))
                .await().indefinitely();

        assertEquals(List.of("housing", "health"), result.stream().map(TopicTagDto::id).toList());
        assertEquals(88L, assignments.postId);
        assertEquals(List.of("housing", "health"), assignments.topicTagIds);
    }

    @Test
    void rejectsAFourthTopicWithoutTouchingTheDatabase() {
        StubTopicRepository topics = new StubTopicRepository(List.of(HOUSING, HEALTH, CRIME));
        StubPostTopicTagRepository assignments = new StubPostTopicTagRepository();
        TopicServiceImpl service = serviceWith(topics, assignments);

        ApiException failure = assertThrows(ApiException.class, () -> service
                .assignCreatorTags(88L, List.of("housing", "health", "crime", "politics"))
                .await().indefinitely());

        assertEquals("TOPIC_TOO_MANY", failure.errorCode());
        // The ceiling is enforced before any query, so an over-long selection costs nothing.
        assertEquals(0, topics.lookups);
        assertNull(assignments.postId);
    }

    @Test
    void acceptsExactlyThreeCreatorTopicTags() {
        StubTopicRepository topics = new StubTopicRepository(List.of(HOUSING, HEALTH, POLITICS));
        StubPostTopicTagRepository assignments = new StubPostTopicTagRepository();
        TopicServiceImpl service = serviceWith(topics, assignments);

        List<TopicTagDto> result = service
                .assignCreatorTags(88L, List.of("housing", "health", "politics"))
                .await().indefinitely();

        assertEquals(List.of("housing", "health", "politics"), assignments.topicTagIds);
        assertEquals(3, result.size());
    }

    @Test
    void rejectsTheSameTopicTwiceRatherThanSilentlyDeduplicating() {
        // Deduplicating would let a client send three ids and receive two topics with no signal —
        // and the composite primary key would reject the second row anyway, as a 500.
        TopicServiceImpl service = serviceWith(
                new StubTopicRepository(List.of(HOUSING)), new StubPostTopicTagRepository());

        ApiException failure = assertThrows(ApiException.class, () -> service
                .assignCreatorTags(88L, List.of("housing", "housing")).await().indefinitely());

        assertEquals("TOPIC_DUPLICATE_SELECTION", failure.errorCode());
    }

    @Test
    void rejectsTheWholeSelectionWhenOneTopicIsUnknownOrRetired() {
        // listActiveByIds answers with only the active matches, which is exactly how a retired
        // topic presents itself. Naming the missing id matters: the author has to know which one.
        StubPostTopicTagRepository assignments = new StubPostTopicTagRepository();
        TopicServiceImpl service = serviceWith(new StubTopicRepository(List.of(HOUSING)), assignments);

        ApiException failure = assertThrows(ApiException.class, () -> service
                .assignCreatorTags(88L, List.of("housing", "retired-topic")).await().indefinitely());

        assertEquals("TOPIC_UNKNOWN", failure.errorCode());
        assertTrue(failure.getMessage().contains("retired-topic"), failure.getMessage());
        // Nothing is attached — the post must not keep the half of the selection that validated.
        assertNull(assignments.postId);
    }

    @Test
    void anEmptySelectionIsValidAndSkipsTheCatalogueLookup() {
        StubTopicRepository topics = new StubTopicRepository(List.of());
        TopicServiceImpl service = serviceWith(topics, new StubPostTopicTagRepository());

        assertEquals(List.of(), service.assignCreatorTags(88L, null).await().indefinitely());
        assertEquals(List.of(), service.assignCreatorTags(88L, List.of()).await().indefinitely());
        assertEquals(0, topics.lookups);
    }

    @Test
    void createsATopicAtTheEndOfTheCatalogueSoItDoesNotDisplaceACuratedTab() {
        StubTopicRepository topics = new StubTopicRepository(List.of());
        topics.nextOrder = 21;
        TopicServiceImpl service = serviceWith(topics, new StubPostTopicTagRepository());

        TopicTagDto created = service.create(
                        new CreateTopicRequest("Cost of living", "Money & business"))
                .await().indefinitely();

        assertEquals("cost-of-living", created.id());
        assertEquals("Cost of living", created.label());
        assertEquals(21, created.displayOrder());
        assertTrue(created.active());
    }

    @Test
    void refusesATopicWhoseDerivedIdAlreadyExists() {
        // "Housing" and "housing " both slugify to `housing`; the second must be a clean 409 rather
        // than a primary-key violation surfacing as a 500.
        StubTopicRepository topics = new StubTopicRepository(List.of());
        topics.existing = HOUSING;
        TopicServiceImpl service = serviceWith(topics, new StubPostTopicTagRepository());

        ApiException failure = assertThrows(ApiException.class, () -> service
                .create(new CreateTopicRequest("Housing", "Society")).await().indefinitely());

        assertEquals("TOPIC_ALREADY_EXISTS", failure.errorCode());
    }

    @Test
    void refusesALabelThatCannotProduceACanonicalId() {
        TopicServiceImpl service = serviceWith(
                new StubTopicRepository(List.of()), new StubPostTopicTagRepository());

        ApiException failure = assertThrows(ApiException.class, () -> service
                .create(new CreateTopicRequest("!!!", "Society")).await().indefinitely());

        assertEquals("TOPIC_LABEL_UNUSABLE", failure.errorCode());
    }

    @Test
    void theFeedRejectsAnUnknownTopicButAcceptsARetiredOne() {
        StubTopicRepository absent = new StubTopicRepository(List.of());
        ApiException failure = assertThrows(ApiException.class, () -> serviceWith(
                absent, new StubPostTopicTagRepository()).requireExists("nope").await().indefinitely());

        assertEquals("TOPIC_FEED_UNKNOWN", failure.errorCode());

        // A retired topic still has a working feed for the posts already filed under it.
        StubTopicRepository retired = new StubTopicRepository(List.of());
        Topic retiredTopic = new Topic("gaming", "Gaming", "Science & technology", 30);
        retiredTopic.setActive(false);
        retired.existing = retiredTopic;
        serviceWith(retired, new StubPostTopicTagRepository())
                .requireExists("gaming").await().indefinitely();
    }

    private static TopicServiceImpl serviceWith(TopicRepository topics,
                                                PostTopicTagRepository assignments) {
        TopicServiceImpl service = new TopicServiceImpl();
        service.topicRepository = topics;
        service.postTopicTagRepository = assignments;
        return service;
    }

    /** Answers catalogue lookups from a fixed set and counts them. */
    private static final class StubTopicRepository extends TopicRepository {
        private final List<Topic> active;
        private Topic existing;
        private int nextOrder = 1;
        private int lookups;

        private StubTopicRepository(List<Topic> active) {
            this.active = active;
        }

        @Override
        public Uni<List<Topic>> listActiveByIds(Collection<String> ids) {
            lookups++;
            return Uni.createFrom().item(active.stream().filter(t -> ids.contains(t.getId())).toList());
        }

        @Override
        public Uni<Topic> findByIdentifier(String id) {
            return Uni.createFrom().item(existing);
        }

        @Override
        public Uni<Integer> nextDisplayOrder() {
            return Uni.createFrom().item(nextOrder);
        }

        @Override
        public Uni<Topic> save(Topic topic) {
            return Uni.createFrom().item(topic);
        }
    }

    /** Records what was attached; null postId means nothing was written. */
    private static final class StubPostTopicTagRepository extends PostTopicTagRepository {
        private Long postId;
        private List<String> topicTagIds;

        @Override
        public Uni<Void> assignCreatorTags(Long postId, List<String> topicTagIds) {
            this.postId = postId;
            this.topicTagIds = new ArrayList<>(topicTagIds);
            return Uni.createFrom().voidItem();
        }
    }
}
