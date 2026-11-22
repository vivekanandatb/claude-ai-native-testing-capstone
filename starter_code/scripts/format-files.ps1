$InputJson = [Console]::In.ReadToEnd()
$InputObject = $InputJson | ConvertFrom-Json

$ToolName = $InputObject.tool_name

if ($ToolName -eq "editFiles" -or $ToolName -eq "createFile") {

    $Files = @()

    if ($null -ne $InputObject.tool_input.files) {
        $Files = $InputObject.tool_input.files
    }
    elseif ($null -ne $InputObject.tool_input.path) {
        $Files = @($InputObject.tool_input.path)
    }

    foreach ($File in $Files) {

        if (-not (Test-Path -LiteralPath $File -PathType Leaf)) {
            continue
        }

        $Extension = [System.IO.Path]::GetExtension($File).ToLowerInvariant()

        switch ($Extension) {

            ".java" {
                # Format only the changed Java file using Spotless
                $AbsolutePath = (Resolve-Path -LiteralPath $File).Path

                mvn spotless:apply `
                    "-DspotlessFiles=$AbsolutePath" `
                    -q

                break
            }

            { $_ -in @(
                ".js", ".jsx",
                ".ts", ".tsx",
                ".json",
                ".css", ".scss",
                ".md",
                ".yaml", ".yml"
            ) } {
                # Format files supported by Prettier
                npx prettier --write -- $File 2>$null

                break
            }
        }
    }
}

Write-Output '{"continue":true}'