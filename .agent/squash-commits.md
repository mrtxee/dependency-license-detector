# Объединение коммитов в один

Скрипт склеивает всю историю репозитория в один коммит с root-родителем.

Выполняется в `/home/a/IdeaProjects/dependency-license-detector`.

## Исходное состояние

| Параметр | Значение |
|---|---|
| Ветка | `master` |
| Remote | отсутствует, поэтому push не требуется |
| Рабочее дерево | чистое, незакоммиченных изменений нет |
| `git user.name` / `user.email` | `mrtxee` / `mrtxee@bk.ru` |

История до операции:

```
6fe9b7d docs: describe squashing the two commits into one
ad9dfc4 increment
27430ec init
```

Коммит с документацией создаётся **до** squash, иначе `.agent/squash-commits.md`
не попадёт в итоговое дерево.

## Почему `commit-tree`, а не `reset --soft`

Обычный приём склейки — `git reset --soft <root>^ && git commit`. Здесь он
неприменим: `27430ec` это root-коммит, у него нет родителя, и «шагнуть назад»
некуда.

`git commit-tree` создаёт новый корневой коммит напрямую, не переводя указатель
ветки, поэтому исходная история остаётся доступной до момента `reset`.

Преимущество перед `git checkout --orphan`: дерево нового коммита берётся из уже
существующего объекта по хешу, поэтому содержимое совпадает байт в байт и не
зависит от того, попали ли в индекс все файлы.

## Важная деталь: не передавать `-p`

Флаг `-p` задаёт родителя. Для root-коммита родителя нет, поэтому `-p` просто
не указывается:

```bash
# НЕПРАВИЛЬНО: git отвергает пустое имя объекта
git commit-tree "$TREE" -p '' -m "$MSG"
# fatal: not a valid object name

# ПРАВИЛЬНО: без -p коммит создаётся без родителя
git commit-tree "$TREE" -m "$MSG"
```

## Команды

```bash
cd /home/a/IdeaProjects/dependency-license-detector

# 1. Зафиксировать исходное состояние, чтобы потом проверить результат
ORIG_HEAD=$(git rev-parse HEAD)
ORIG_TREE=$(git rev-parse HEAD^{tree})

# 2. Сообщение нового коммита
MSG="Dependency license detector: initial implementation"

# 3. Создать новый корневой коммит с деревом текущего HEAD (без -p!)
NEW_COMMIT=$(git commit-tree "$ORIG_TREE" -m "$MSG")

# 4. Перевести ветку master на новый коммит.
#    --soft сохраняет индекс и рабочее дерево, файлы на диске не трогаются.
git reset --soft "$NEW_COMMIT"
```

Если скрипт исполняется через обёртку, которая не переживает переносы строк,
каждую команду стоит выполнять отдельным вызовом, сохраняя промежуточные
значения в файлы (`git rev-parse HEAD > /tmp/orig_head.txt`).

## Проверка

```bash
# Стал ровно один коммит
git rev-list --count HEAD

# Дерево нового коммита идентично дереву до squash: вывод пустой
git diff "$ORIG_HEAD" HEAD --stat

# Новый коммит не имеет родителя: в выводе только сам коммит
git rev-list --parents -n 1 HEAD

# Рабочее дерево чистое
git status --short

# Сообщение нового коммита
git log --oneline -5
```

## Откат

Пока выполнен только `git reset --soft`, исходные коммиты остаются в базе
объектов, и в `reflog` видно все предыдущие состояния ветки:

```bash
git --no-pager reflog -5
git reset --soft 6fe9b7d734fe5990a71242ab0ce82f391ff13f68
```

Хеш `6fe9b7d734fe5990a71242ab0ce82f391ff13f68` соответствует состоянию до
squash. Он же остаётся валидным после запуска garbage collection, потому что
достижим из reflog записи ветки `master`.

После `git reflog expire --expire=now --all && git gc --prune=now` исходные
коммиты удаляются безвозвратно.
