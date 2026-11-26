"""Clone or update Dash on disk, then start the Windows RTX launcher."""

import msvcrt
import os
from pathlib import Path
import shutil
import subprocess


REPO = "https://github.com/DashCEO/dash-client-.git"
PROJECT = Path.home() / "Dash-Client-GitHub"
BRANCH = "main"
UPDATE_TIMEOUT = 30


def git_output(git, *args, env=None, timeout=None):
    return subprocess.run(
        [git, "-C", str(PROJECT), *args],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
        timeout=timeout,
    ).stdout.strip()


def update_existing(git):
    if git_output(git, "rev-parse", "--abbrev-ref", "HEAD") != BRANCH:
        print(f"Автооновлення працює для гілки {BRANCH}. Запускаю поточну копію.")
        return
    if git_output(git, "status", "--porcelain"):
        print("У Dash є локальні правки. Запускаю твою копію без оновлення.")
        return

    current = git_output(git, "rev-parse", "HEAD")
    upstream = f"refs/remotes/origin/{BRANCH}"
    env = os.environ.copy()
    env["GIT_TERMINAL_PROMPT"] = "0"
    env["GCM_INTERACTIVE"] = "Never"
    print("Перевіряю оновлення Dash на GitHub...")
    try:
        git_output(
            git, "fetch", "--no-tags", "origin", f"refs/heads/{BRANCH}:{upstream}",
            env=env, timeout=UPDATE_TIMEOUT,
        )
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        print("Не вдалося перевірити GitHub. Запускаю встановлену версію.")
        return

    try:
        git_output(git, "merge", "--ff-only", "--no-overwrite-ignore", upstream)
    except subprocess.CalledProcessError:
        print("Git не зміг безпечно застосувати оновлення. Запускаю встановлену версію.")
        return

    updated = git_output(git, "rev-parse", "HEAD")
    if updated == current:
        print("Оновлень для поточної версії немає.")
    else:
        print(f"Dash оновлено до {updated[:7]}.")


def main():
    git = shutil.which("git")
    if git is None:
        print("Git не знайдено. Встанови Git for Windows і перезапусти IDLE.")
        return

    existing = PROJECT.exists()
    if not existing:
        print(f"Клоную Dash у {PROJECT}")
        subprocess.run(
            [git, "clone", "--branch", BRANCH, "--single-branch", REPO, str(PROJECT)],
            check=True,
            creationflags=subprocess.CREATE_NEW_CONSOLE,
        )

    root = Path(git_output(git, "rev-parse", "--show-toplevel"))
    origin = git_output(git, "remote", "get-url", "origin")
    if root.resolve() != PROJECT.resolve() or origin != REPO:
        print(f"Папка {PROJECT} не є копією потрібного репозиторію Dash.")
        return

    lock_path = PROJECT / ".gradle" / "dash-client-launch.lock"
    lock_path.parent.mkdir(exist_ok=True)
    try:
        lock = lock_path.open("a+b")
    except PermissionError:
        print("Dash уже запущений або файл блокування зайнятий.")
        return
    with lock:
        lock.seek(0)
        try:
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            print("Інший запуск Dash уже перевіряє оновлення.")
            return
        if existing:
            update_existing(git)

    if not (PROJECT / "DashClient-RTX.bat").is_file():
        print(f"У папці {PROJECT} відсутній DashClient-RTX.bat.")
        return

    subprocess.Popen(
        ["cmd.exe", "/d", "/c", "call", "DashClient-RTX.bat"],
        cwd=PROJECT,
        creationflags=subprocess.CREATE_NEW_CONSOLE,
    )


if __name__ == "__main__":
    try:
        main()
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"Не вдалося запустити Dash. {error}")
