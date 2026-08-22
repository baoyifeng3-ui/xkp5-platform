# Current Stack Verification

Run the application from the current checkout with rebuilt images:

```powershell
./deploy/dev/start-current-stack.ps1 -EnvFile .env.prod
```

The script requires Docker Desktop, rebuilds both `java` and `vue`, starts only
those application services with the compose dependencies, and waits for the
backend health endpoint. It never removes volumes or resets database data.
Use an explicit environment file with real credentials; `.env.prod.example`
only documents required keys and is not a production secret source.
