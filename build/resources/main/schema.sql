create table routerinfo (
    hash bytea primary key,
    cert bytea unique not null
);


create table addresses (
    idx serial primary key,
    host text not null,
    port integer unique not null
);


insert into addresses (host, integer) values ('localhost', 5432);
insert into addresses (host, integer) values ('dummy', 61524);
