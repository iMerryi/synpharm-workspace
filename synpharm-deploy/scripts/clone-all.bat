@echo off
REM =============================================================================
REM SynPharm 工作区检出 + 开发环境准备
REM
REM 2026-11-04 起工作区是**单仓库**（Ljz4777/synpharm-workspace）：
REM Java 服务直接位于仓库根，与 frontend / fastapi / deploy 平级，
REM 不再有 synpharm-cloud\ 这一层。本脚本因此从「拉 4 个仓库」简化为「拉 1 个」。
REM
REM 用法：
REM   clone-all.bat [目标目录] [仓库 URL]
REM
REM 拉取后启动开发环境：
REM   cd synpharm-workspace
REM   copy synpharm-deploy\.env.example synpharm-deploy\.env
REM   docker compose -f synpharm-deploy\docker-compose.dev.yml up -d
REM =============================================================================

setlocal

set TARGET_DIR=%~1
if "%TARGET_DIR%"=="" set TARGET_DIR=.\synpharm-workspace

set REPO_URL=%~2
if "%REPO_URL%"=="" set REPO_URL=https://github.com/Ljz4777/synpharm-workspace.git

if exist "%TARGET_DIR%\.git" (
  echo === %TARGET_DIR% 已存在，拉取最新 ===
  git -C "%TARGET_DIR%" pull --ff-only
  if errorlevel 1 echo   WARN: pull 失败，请手工检查
) else (
  echo === 克隆 %REPO_URL% ===
  git clone "%REPO_URL%" "%TARGET_DIR%"
  if errorlevel 1 (
    echo   ERROR: clone 失败
    exit /b 1
  )
)

if not exist "%TARGET_DIR%\synpharm-deploy\.env" (
  if exist "%TARGET_DIR%\synpharm-deploy\.env.example" (
    copy "%TARGET_DIR%\synpharm-deploy\.env.example" "%TARGET_DIR%\synpharm-deploy\.env" >nul
    echo === 已从 .env.example 生成 synpharm-deploy\.env，请先填写口令与密钥 ===
  )
)

echo.
echo === 目录结构（Java 服务直接位于仓库根）===
dir /b "%TARGET_DIR%" | findstr /b "synpharm-"
echo.
echo 下一步：
echo   cd %TARGET_DIR%
echo   REM 编辑 synpharm-deploy\.env：数据库口令 / JWT 密钥 / 内部令牌 / 算法引擎 API Key
echo   docker compose -f synpharm-deploy\docker-compose.dev.yml up -d
endlocal