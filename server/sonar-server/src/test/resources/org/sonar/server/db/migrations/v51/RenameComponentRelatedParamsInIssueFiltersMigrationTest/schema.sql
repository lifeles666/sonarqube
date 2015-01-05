CREATE TABLE "ISSUE_FILTERS" (
  "ID" INTEGER NOT NULL GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
  "NAME" VARCHAR(100) NOT NULL,
  "SHARED" BOOLEAN NOT NULL DEFAULT FALSE,
  "USER_LOGIN" VARCHAR(255),
  "DESCRIPTION" VARCHAR(4000),
  "DATA" CLOB(2147483647),
  "CREATED_AT" TIMESTAMP,
  "UPDATED_AT" TIMESTAMP
);