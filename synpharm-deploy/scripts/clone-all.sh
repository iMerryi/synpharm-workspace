#!/usr/bin/env bash
# =============================================================================
# SynPharm 工作区检出 + 开发环境准备
#
# 2026-11-04 起工作区是**单仓库**（Ljz4777/synpharm-workspace）：
# Java 服务直接位于仓库根，与 frontend / fastapi / deploy 平级，
# 不再有 synpharm-cloud/ 这一层。本脚本因此从「拉 4 个仓库」简化为「拉 1 个」。
#
# 用法：
#   ./clone-all.sh [目标目录] [仓库 URL]
#   默认目标目录：./synpharm-workspace
#   默认仓库 URL：https://github.com/Ljz4777/synpharm-workspace.git
#
# 拉取后启动开发环境：
#   cd synpharm-workspace
#   cp synpharm-deploy/.env.example synpharm-deploy/.env   # 填写口令/密钥
#   docker compose -f synpharm-deploy/docker-compose.dev.yml up -d
# =============================================================================
set -euo pipefail

TARGET_DIR="${1:-./synpharm-workspace}"
REPO_URL="${2:-https://github.com/Ljz4777/synpharm-workspace.git}"

if [ -d "$TARGET_DIR/.git" ]; then
  echo "=== $TARGET_DIR 已存在，拉取最新 ==="
  git -C "$TARGET_DIR" pull --ff-only || echo "  WARN: pull 失败，请手工检查"
else
  echo "=== 克隆 $REPO_URL ==="
  git clone "$REPO_URL" "$TARGET_DIR"
fi

# .env 里是真实口令，不入库；首次检出时从模板生成
if [ ! -f "$TARGET_DIR/synpharm-deploy/.env" ] && [ -f "$TARGET_DIR/synpharm-deploy/.env.example" ]; then
  cp "$TARGET_DIR/synpharm-deploy/.env.example" "$TARGET_DIR/synpharm-deploy/.env"
  echo "=== 已从 .env.example 生成 synpharm-deploy/.env，请先填写口令与密钥 ==="
fi

echo ""
echo "=== 目录结构（Java 服务直接位于仓库根）==="
ls -1 "$TARGET_DIR" | grep -E '^synpharm-' || true
echo ""
echo "下一步："
echo "  cd $TARGET_DIR"
echo "  # 编辑 synpharm-deploy/.env：数据库口令 / JWT 密钥 / 内部令牌 / 算法引擎 API Key"
echo "  docker compose -f synpharm-deploy/docker-compose.dev.yml up -d"