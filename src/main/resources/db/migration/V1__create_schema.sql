create table interest_group (deleted boolean not null, created_at timestamp(6), created_by uuid, id uuid not null, description varchar(255) not null, name varchar(255) not null, primary key (id));
comment on column interest_group.deleted is 'Soft-delete indicator';
create table interest_group_membership (joined_at timestamp(6), interest_group_id uuid not null, user_id uuid not null, role varchar(255) check ((role in ('ADMIN','MEMBER'))), status varchar(255) check ((status in ('ACCEPTED','PENDING','DENIED','WITHDREW','BANNED'))), primary key (interest_group_id, user_id));
create table interest_group_tags (interest_group_id uuid not null, tags varchar(100));
alter table if exists interest_group_membership add constraint FK969x3gmh9kq16vevdr74h0t3g foreign key (interest_group_id) references interest_group;
alter table if exists interest_group_tags add constraint FKcbscsmlvmrmdqc0ih8c6dlgkk foreign key (interest_group_id) references interest_group;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
