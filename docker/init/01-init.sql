-- AIMS PostgreSQL 初始化脚本
-- 说明：docker-entrypoint-initdb.d 中的脚本在 POSTGRES_DB（默认 aims）库内执行，
-- 因此无需再 CREATE DATABASE，只需启用 pgvector 扩展（P2 RAG 使用）。
CREATE EXTENSION IF NOT EXISTS vector;
