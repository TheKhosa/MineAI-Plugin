/**
 * TeamCity Build Configuration Script
 * Programmatically sets up the AgentSensorPlugin build using TeamCity REST API
 *
 * Usage: node teamcity-setup.js
 */

const https = require('https');
const http = require('http');

// TeamCity Configuration
const TEAMCITY_CONFIG = {
    url: 'http://145.239.253.161:8111',
    username: 'AIAgent',
    password: 'D#hp^uC5RuJcn%',
    projectId: 'MineRL',
    projectName: 'MineRL Agent System',
    buildTypeId: 'AgentSensorPlugin',
    buildTypeName: 'AgentSensorPlugin',
    vcsRootName: 'MineRL Repository',
    gitUrl: 'https://github.com/TheKhosa/MineAI.git',
    gitBranch: 'refs/heads/main'
};

/**
 * Make authenticated HTTP request to TeamCity REST API
 */
function teamcityRequest(method, path, body = null) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(TEAMCITY_CONFIG.url);
        const isHttps = urlObj.protocol === 'https:';
        const httpModule = isHttps ? https : http;

        const auth = Buffer.from(`${TEAMCITY_CONFIG.username}:${TEAMCITY_CONFIG.password}`).toString('base64');

        const options = {
            hostname: urlObj.hostname,
            port: urlObj.port || (isHttps ? 443 : 80),
            path: path,
            method: method,
            headers: {
                'Authorization': `Basic ${auth}`,
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            }
        };

        if (body) {
            const bodyStr = JSON.stringify(body);
            options.headers['Content-Length'] = Buffer.byteLength(bodyStr);
        }

        const req = httpModule.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                    try {
                        resolve(data ? JSON.parse(data) : null);
                    } catch (e) {
                        resolve(data);
                    }
                } else {
                    reject(new Error(`HTTP ${res.statusCode}: ${data}`));
                }
            });
        });

        req.on('error', reject);

        if (body) {
            req.write(JSON.stringify(body));
        }

        req.end();
    });
}

/**
 * Step 1: Create or verify project exists
 */
async function createProject() {
    console.log('\n[1/7] Creating project...');

    try {
        // Check if project exists
        const project = await teamcityRequest('GET', `/app/rest/projects/id:${TEAMCITY_CONFIG.projectId}`);
        console.log(`✓ Project already exists: ${project.name}`);
        return project;
    } catch (error) {
        // Project doesn't exist, create it
        console.log('Project not found, creating...');
        const newProject = await teamcityRequest('POST', '/app/rest/projects', {
            id: TEAMCITY_CONFIG.projectId,
            name: TEAMCITY_CONFIG.projectName,
            description: 'AI Agent System for Minecraft with ML training and intelligent villages'
        });
        console.log(`✓ Created project: ${newProject.name}`);
        return newProject;
    }
}

/**
 * Step 2: Create VCS Root
 */
async function createVcsRoot() {
    console.log('\n[2/7] Creating VCS root...');

    const vcsRootId = `${TEAMCITY_CONFIG.projectId}_GitRoot`;

    try {
        // Check if VCS root exists
        const vcsRoot = await teamcityRequest('GET', `/app/rest/vcs-roots/id:${vcsRootId}`);
        console.log(`✓ VCS root already exists: ${vcsRoot.name}`);
        return vcsRoot;
    } catch (error) {
        // VCS root doesn't exist, create it
        console.log('VCS root not found, creating...');
        const newVcsRoot = await teamcityRequest('POST', '/app/rest/vcs-roots', {
            id: vcsRootId,
            name: TEAMCITY_CONFIG.vcsRootName,
            project: { id: TEAMCITY_CONFIG.projectId },
            vcsName: 'jetbrains.git',
            properties: {
                property: [
                    { name: 'url', value: TEAMCITY_CONFIG.gitUrl },
                    { name: 'branch', value: TEAMCITY_CONFIG.gitBranch },
                    { name: 'authMethod', value: 'ANONYMOUS' },
                    { name: 'usernameStyle', value: 'USERID' }
                ]
            }
        });
        console.log(`✓ Created VCS root: ${newVcsRoot.name}`);
        return newVcsRoot;
    }
}

/**
 * Step 3: Create Build Configuration
 */
async function createBuildConfiguration(vcsRootId) {
    console.log('\n[3/7] Creating build configuration...');

    try {
        // Check if build config exists
        const buildType = await teamcityRequest('GET', `/app/rest/buildTypes/id:${TEAMCITY_CONFIG.buildTypeId}`);
        console.log(`✓ Build configuration already exists: ${buildType.name}`);
        return buildType;
    } catch (error) {
        // Build config doesn't exist, create it with minimal payload
        console.log('Build configuration not found, creating...');
        const newBuildType = await teamcityRequest('POST', `/app/rest/projects/id:${TEAMCITY_CONFIG.projectId}/buildTypes`, {
            name: TEAMCITY_CONFIG.buildTypeName,
            id: TEAMCITY_CONFIG.buildTypeId
        });
        console.log(`✓ Created build configuration: ${newBuildType.name}`);

        // Attach VCS root separately
        await teamcityRequest('POST', `/app/rest/buildTypes/id:${TEAMCITY_CONFIG.buildTypeId}/vcs-root-entries`, {
            'vcs-root': { id: vcsRootId },
            'checkout-rules': ''
        });
        console.log(`✓ Attached VCS root to build configuration`);

        return newBuildType;
    }
}

/**
 * Step 4: Configure Build Steps
 */
async function configureBuildSteps() {
    console.log('\n[4/7] Configuring build steps...');

    const steps = [
        {
            name: 'Maven Clean',
            type: 'Maven2',
            properties: {
                property: [
                    { name: 'goals', value: 'clean' },
                    { name: 'pomLocation', value: 'AgentSensorPlugin/pom.xml' },
                    { name: 'mavenVersion', value: 'DEFAULT' }
                ]
            }
        },
        {
            name: 'Maven Compile',
            type: 'Maven2',
            properties: {
                property: [
                    { name: 'goals', value: 'compile' },
                    { name: 'pomLocation', value: 'AgentSensorPlugin/pom.xml' },
                    { name: 'mavenVersion', value: 'DEFAULT' }
                ]
            }
        },
        {
            name: 'Maven Package',
            type: 'Maven2',
            properties: {
                property: [
                    { name: 'goals', value: 'package' },
                    { name: 'pomLocation', value: 'AgentSensorPlugin/pom.xml' },
                    { name: 'mavenVersion', value: 'DEFAULT' },
                    { name: 'runnerArgs', value: '-DskipTests' }
                ]
            }
        }
    ];

    for (const step of steps) {
        try {
            await teamcityRequest('POST', `/app/rest/buildTypes/id:${TEAMCITY_CONFIG.buildTypeId}/steps`, step);
            console.log(`✓ Added build step: ${step.name}`);
        } catch (error) {
            console.log(`⚠ Build step already exists or error: ${step.name}`);
        }
    }
}

/**
 * Step 5: Configure Artifact Paths
 */
async function configureArtifacts() {
    console.log('\n[5/7] Configuring artifact paths...');

    try {
        await teamcityRequest('PUT',
            `/app/rest/buildTypes/id:${TEAMCITY_CONFIG.buildTypeId}/settings/artifactRules`,
            'AgentSensorPlugin/target/AgentSensorPlugin-*.jar => AgentSensorPlugin.jar',
            {
                headers: {
                    'Content-Type': 'text/plain'
                }
            }
        );
        console.log('✓ Configured artifact paths');
    } catch (error) {
        console.log(`⚠ Could not configure artifacts: ${error.message}`);
    }
}

/**
 * Step 6: Configure VCS Trigger
 */
async function configureVcsTrigger() {
    console.log('\n[6/7] Configuring VCS trigger...');

    try {
        const trigger = {
            type: 'vcsTrigger',
            properties: {
                property: [
                    { name: 'quietPeriod', value: '60' },
                    { name: 'triggerRules', value: '' }
                ]
            }
        };

        await teamcityRequest('POST', `/app/rest/buildTypes/id:${TEAMCITY_CONFIG.buildTypeId}/triggers`, trigger);
        console.log('✓ Configured VCS trigger (60s quiet period)');
    } catch (error) {
        console.log(`⚠ VCS trigger already exists or error: ${error.message}`);
    }
}

/**
 * Step 7: Configure Scheduled Trigger (Optional - Daily 2 AM)
 */
async function configureScheduledTrigger() {
    console.log('\n[7/7] Configuring scheduled trigger...');

    try {
        const trigger = {
            type: 'schedulingTrigger',
            properties: {
                property: [
                    { name: 'cronExpression_sec', value: '0' },
                    { name: 'cronExpression_min', value: '0' },
                    { name: 'cronExpression_hour', value: '2' },
                    { name: 'cronExpression_day', value: '*' },
                    { name: 'cronExpression_month', value: '*' },
                    { name: 'cronExpression_year', value: '?' },
                    { name: 'triggerBuildWithPendingChangesOnly', value: 'false' }
                ]
            }
        };

        await teamcityRequest('POST', `/app/rest/buildTypes/id:${TEAMCITY_CONFIG.buildTypeId}/triggers`, trigger);
        console.log('✓ Configured scheduled trigger (daily at 2 AM)');
    } catch (error) {
        console.log(`⚠ Scheduled trigger already exists or error: ${error.message}`);
    }
}

/**
 * Main execution
 */
async function main() {
    console.log('===========================================');
    console.log('TeamCity Build Configuration Setup');
    console.log('===========================================');
    console.log(`Target: ${TEAMCITY_CONFIG.url}`);
    console.log(`Project: ${TEAMCITY_CONFIG.projectName}`);
    console.log(`Build: ${TEAMCITY_CONFIG.buildTypeName}`);

    // Check Git URL
    if (TEAMCITY_CONFIG.gitUrl === 'YOUR_GIT_REPOSITORY_URL') {
        console.warn('\n⚠ WARNING: Git URL not configured!');
        console.warn('Please update TEAMCITY_CONFIG.gitUrl in this script.');
        console.warn('Continuing with placeholder URL...\n');
    }

    try {
        // Execute setup steps
        await createProject();
        const vcsRoot = await createVcsRoot();
        await createBuildConfiguration(vcsRoot.id);
        await configureBuildSteps();
        await configureArtifacts();
        await configureVcsTrigger();
        await configureScheduledTrigger();

        console.log('\n===========================================');
        console.log('✓ Setup Complete!');
        console.log('===========================================');
        console.log(`\nBuild Configuration URL:`);
        console.log(`${TEAMCITY_CONFIG.url}/buildConfiguration/${TEAMCITY_CONFIG.buildTypeId}`);
        console.log(`\nNext Steps:`);
        console.log(`1. Update the Git URL in this script if needed`);
        console.log(`2. Run a manual build to test: ${TEAMCITY_CONFIG.url}/buildConfiguration/${TEAMCITY_CONFIG.buildTypeId}`);
        console.log(`3. Deploy the plugin JAR to your Minecraft server's plugins folder`);
        console.log(`4. Plugin will auto-update every 30 minutes from TeamCity\n`);

    } catch (error) {
        console.error('\n✗ Setup failed:', error.message);
        console.error('Please check your TeamCity credentials and network connection.');
        process.exit(1);
    }
}

// Run setup
main();
