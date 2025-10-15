# TeamCity Setup Guide for AgentSensorPlugin

## Overview
This guide will help you set up automatic builds and deployments for the AgentSensorPlugin using TeamCity CI.

## TeamCity Server
- **URL**: http://145.239.253.161:8111/
- **Username**: AIAgent
- **Password**: D#hp^uC5RuJcn%

## Step 1: Create Project in TeamCity

1. Log in to TeamCity web interface
2. Click "Create Project"
3. Enter project details:
   - **Project Name**: MineRL Agent System
   - **Project ID**: MineRL

## Step 2: Create Build Configuration

1. Inside the project, click "Create Build Configuration"
2. Enter configuration details:
   - **Name**: AgentSensorPlugin
   - **Build Configuration ID**: `AgentSensorPlugin` (IMPORTANT: Must match the ID in plugin code!)

## Step 3: Configure VCS Root

1. Click "Create VCS Root"
2. Choose your VCS type (Git)
3. Configure repository:
   - **VCS Root Name**: MineRL Repository
   - **Fetch URL**: `[Your Git Repository URL]`
   - **Default Branch**: main
   - **Authentication**: Configure as needed

## Step 4: Add Build Steps

Add the following build steps in order:

### Step 1: Maven Clean
- **Runner Type**: Maven
- **Goals**: `clean`
- **POM Location**: `AgentSensorPlugin/pom.xml`

### Step 2: Maven Compile
- **Runner Type**: Maven
- **Goals**: `compile`
- **POM Location**: `AgentSensorPlugin/pom.xml`

### Step 3: Maven Package
- **Runner Type**: Maven
- **Goals**: `package`
- **POM Location**: `AgentSensorPlugin/pom.xml`
- **Additional Maven Arguments**: `-DskipTests`

## Step 5: Configure Artifacts

1. Go to "General Settings" → "Artifact Paths"
2. Add artifact path:
   ```
   AgentSensorPlugin/target/AgentSensorPlugin-*.jar => AgentSensorPlugin.jar
   ```

This ensures the built JAR is published with a consistent name.

## Step 6: Configure Build Triggers

Add the following triggers:

### VCS Trigger
- **Type**: VCS Trigger
- **Quiet Period**: 60 seconds
- **Description**: Trigger on git push

### Scheduled Trigger (Optional)
- **Type**: Schedule Trigger
- **Cron Expression**: `0 2 * * * ?` (Daily at 2 AM)
- **Description**: Nightly build

## Step 7: Verify Build

1. Click "Run" to trigger first build
2. Monitor build progress in console
3. Verify artifact is published after successful build

## Step 8: Test Auto-Update

1. Place the plugin in your Minecraft server's `plugins/` folder
2. Start the server
3. Plugin will check TeamCity for updates every 30 minutes
4. Manual update check: `/agentsensor update`

## API Access for Plugin

The plugin uses TeamCity REST API to check for updates:

**Endpoint**: `GET /app/rest/builds?locator=buildType:AgentSensorPlugin,status:SUCCESS,count:1`

**Authentication**: Basic Auth (username:password encoded in Base64)

## Troubleshooting

### Build Fails
- Check Maven installation in TeamCity
- Verify Java 17+ is available
- Check pom.xml for errors

### Artifact Not Published
- Verify artifact path matches actual build output
- Check build log for packaging errors

### Plugin Can't Download Updates
- Verify TeamCity server is accessible from Minecraft server
- Check credentials in plugin code
- Verify Build Type ID matches exactly

## Security Notes

- TeamCity credentials are hardcoded in plugin for demo purposes
- In production, use environment variables or config files
- Consider using TeamCity API tokens instead of password auth
- Restrict TeamCity guest access if needed

## Commands

Once plugin is installed:

```
/agentsensor status   - Show plugin and TeamCity status
/agentsensor update   - Manually check for updates
/agentsensor reload   - Reload plugin configuration
/agentsensor sensors  - List available sensors
```

## Build Artifacts

Each successful build produces:
- `AgentSensorPlugin.jar` - Shaded JAR with all dependencies
- Build number in artifact metadata
- Version from pom.xml

## Next Steps

1. Set up webhook for automatic deployments
2. Configure build notifications (email/Slack)
3. Add automated tests to build pipeline
4. Set up staging vs. production build configurations
