# Support question as primary post heading

## Goal

Make the support question the only post title across persistence, API and mobile UI.

## Implementation

1. Drop `post.title` in a new Liquibase migration and remove it from the entity and API records.
2. Update seed data and post/vote integration fixtures for the new database shape.
3. Remove headline state and validation from creation, placing the support question before the summary.
4. Render the support question once in the former headline position and give the scrolling article body the reclaimed height with slightly larger body type.
5. Update contract and component tests, then run targeted backend and frontend verification.
