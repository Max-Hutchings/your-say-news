package com.yoursay.topics.model;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class TopicRepository implements PanacheRepositoryBase<Topic, String> {

    private static final String CATALOGUE_ORDER = " order by t.displayOrder asc, t.id asc";

    /** The catalogue readers see: active topics only, in tab-strip order. */
    public Uni<List<Topic>> listActive() {
        return find("from Topic t where t.active = true" + CATALOGUE_ORDER).list();
    }

    /** Every topic including retired ones — the admin view. */
    public Uni<List<Topic>> listAll() {
        return find("from Topic t" + CATALOGUE_ORDER).list();
    }

    public Uni<Topic> findByIdentifier(String id) {
        return findById(id);
    }

    /**
     * The active topics among {@code ids}. Used to validate an author's selection in one query:
     * comparing the result size against the request tells us whether any id was unknown or retired.
     */
    public Uni<List<Topic>> listActiveByIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        return find("from Topic t where t.active = true and t.id in :ids" + CATALOGUE_ORDER,
                Parameters.with("ids", ids)).list();
    }

    /**
     * The next free tab-strip position. A new topic goes to the end so it appears in the dropdown
     * rather than displacing a curated tab.
     */
    public Uni<Integer> nextDisplayOrder() {
        // A scalar aggregate, so this goes through the session directly — Panache's find() returns
        // entities and cannot project one.
        return getSession().chain(session -> session
                .createQuery("select coalesce(max(t.displayOrder), 0) + 1 from Topic t", Integer.class)
                .getSingleResult());
    }

    public Uni<Topic> save(Topic topic) {
        return persist(topic).replaceWith(topic);
    }
}
