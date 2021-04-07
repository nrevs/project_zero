
FROM postgres
ENV POSTGRES_USER projzero
ENV POSTGRES_PASSWORD projzero
ADD schema.sql /docker-entrypoint-initdb.d
EXPOSE 5432
