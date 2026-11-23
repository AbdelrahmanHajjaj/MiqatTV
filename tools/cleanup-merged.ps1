param([Parameter(Mandatory)][string[]]$Branch)

$ErrorActionPreference = 'Stop'
$common = git -C $PSScriptRoot rev-parse --path-format=absolute --git-common-dir
if ($LASTEXITCODE -ne 0) { throw 'Cannot locate the primary checkout.' }
$root = Split-Path -Parent $common
git -C $root fetch --prune origin
if ($LASTEXITCODE -ne 0) { throw 'Cannot verify the current origin/main.' }
$merged = git -C $root for-each-ref --merged=origin/main '--format=%(refname:short)' refs/heads
if ($LASTEXITCODE -ne 0) { throw 'Cannot list merged branches.' }
$worktrees = (git -C $root worktree list --porcelain) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Cannot list worktrees.' }

$managed = Join-Path $root 'state/worktrees'
$archive = Join-Path $root 'state/worktree-archive'
foreach ($folder in @((Join-Path $root 'state'), $managed, $archive)) {
    if ((Test-Path -LiteralPath $folder) -and
        ((Get-Item -LiteralPath $folder -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Cleanup requires a directory in the primary checkout: $folder"
    }
}

$generated = '^!! (?:\.gradle/|build/|(?:companion/(?:android|core)|installer(?:/runtime)?|platforms/android-tv|platforms/webos|shared/core)/(?:build|\.gradle|\.kotlin|node_modules)/|site/(?:\.astro/|dist/|node_modules/|public/licenses/|src/assets/fonts/|src/data/background-patterns\.json|src/styles/tokens\.css))$'
foreach ($name in $Branch) {
    if ($name -eq 'main' -or $merged -cnotcontains $name) {
        Write-Warning "Keeping $name because it is main or its tip is absent from origin/main."
        continue
    }

    $entry = ($worktrees -split "`n`n") | Where-Object {
        ($_ -split "`n") -ccontains "branch refs/heads/$name"
    }
    if ($entry) {
        $lines = $entry -split "`n"
        $path = (Resolve-Path -LiteralPath $lines[0].Substring(9)).Path
        if ((Split-Path -Parent $path) -ne [IO.Path]::GetFullPath($managed) -or
            ($lines -match '^locked(?: |$)') -or
            ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            Write-Warning "Keeping worktree outside the managed folder or locked: $path"
            continue
        }

        $status = git -C $path status --porcelain --untracked-files=all --ignored=matching
        if ($LASTEXITCODE -ne 0) { throw "Cannot inspect $path." }
        if ($status | Where-Object { $_ -ne '!! state/' -and $_ -notmatch $generated }) {
            Write-Warning "Keeping worktree with modified or unrecognized files: $path"
            continue
        }

        $state = Join-Path $path 'state'
        if (Test-Path -LiteralPath $state) {
            $target = Join-Path $archive ("{0}-{1}" -f (Split-Path -Leaf $path), $lines[1].Substring(5, 12))
            if (Test-Path -LiteralPath $target) { throw "Archive already exists: $target" }
            New-Item -ItemType Directory -Force -Path $archive | Out-Null
            Move-Item -LiteralPath $state -Destination $target
            Write-Output "Archived private state to $target"
        }
        git -C $root -c core.longpaths=true worktree remove -- $path
        if ($LASTEXITCODE -ne 0) { throw "Could not remove $path." }
    }

    git -C $root branch -d -- $name
    if ($LASTEXITCODE -ne 0) { throw "Could not delete $name." }
}
