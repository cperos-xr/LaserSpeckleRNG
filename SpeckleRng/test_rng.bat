@echo off
setlocal enabledelayedexpansion

set "API=http://192.168.0.50:8080/rng?min=1&max=100"
set "COUNT=10000"
set "RESULTS="

REM Loop 10000 times
for /L %%i in (1,1,%COUNT%) do (
    for /f "tokens=*" %%a in ('curl -s "%API%" ^| findstr /C:"\"value\":"') do (
        set "LINE=%%a"
        REM Extract the value after '"value":'
        for /f "tokens=2 delims=:," %%b in ("!LINE!") do set "VAL=%%b"
        set "VAL=!VAL: =!"
        set "RESULTS=!RESULTS!!VAL! "
    )
)

REM Save results to file
(echo !RESULTS!) > rng_results.txt

REM Calculate sum and frequency
set /a SUM=0
for %%v in (!RESULTS!) do (
    set /a SUM+=%%v
)
set /a MEAN=SUM/COUNT

echo Mean: !MEAN!

for /L %%n in (1,1,10) do (
    set /a FREQ=0
    for %%v in (!RESULTS!) do (
        if %%v==%%n set /a FREQ+=1
    )
    echo Number %%n: !FREQ! times
)

endlocal