# Test roadmap

Fixture for `test_issue_sync_check.py`. Not the real roadmap.

## 7. Backlog

### E1 · Identity

#### E1-S1 · Resource server
Labels: `backend` · Size: M · Blocked by: none
- **AC**
  - [ ] A request without a token returns `401`
- **Tasks**
  - [ ] Security configuration

#### E1-S2 · Users and links
Labels: `backend` · Size: M · Blocked by: E1-S1
- **AC**
  - [ ] `GET /users?role=STUDENT&q=sa` returns a page of matching users for an admin
  - [ ] Creating a guardian link returns `201`; the same pair again returns `409`
  - [ ] Deleting a link returns `204`, and `404` the second time
- **Tests:** unit
- **Tasks**
  - [ ] Migration and entities

## 8. GitHub tracking

| Epic | Issue | Stories (sub-issues) |
|---|---|---|
| E1 | #66 | S1 #67, S2 #68 |
