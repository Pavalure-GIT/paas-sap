pipeline{
    agent any

    parameters {
        string defaultValue: 'subscription', description: 'enter azure subscription where compartment lives', name: 'subscription', trim: true
        string defaultValue: '-', description: 'Enter resource group', name: 'resource_group', trim: true
        string defaultValue: '-', description: 'Enter dr resource group', name: 'dr_resource_group', trim: true
        choice choices: ['true', 'false'], description: 'select true if you want to tear down dr, select false otherwise', name: 'dr'
        choice choices: ['true', 'false'], description: '', name: 'terminate'
    }

     //get keys from vault
    environment {
        AZURE_CLIENT_ID = vault path: "secret/${params.subscription}/automation/azure/client_id", key: 'value', vaultUrl: 'http://10.165.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
        AZURE_SECRET = vault path: "secret/${params.subscription}/automation/azure/client_secret", key: 'value', vaultUrl: 'http://10.165.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
        AZURE_SUBSCRIPTION_ID = vault path: "secret/${params.subscription}/automation/azure/subscription_id", key: 'value', vaultUrl: 'http://10.165.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
        AZURE_TENANT = vault path: "secret/${params.subscription}/automation/azure/tenant_id", key: 'value', vaultUrl: 'http://10.165.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
    }


    stages{

        stage('git'){
            steps{
                git credentialsId: 'daivdprivatekeygit', url: 'e4s@vs-ssh.visualstudio.com:v3/e4s/E4S-PublicCloud/SystemTeam'
            }
        }
        stage('Azure'){
            steps{
                sh """
                set +x
                virtualenv .
                source bin/activate
                pip install -r azure_requirements.txt
                export AZURE_CLIENT_ID=${env.AZURE_CLIENT_ID}
                export AZURE_SECRET=${env.AZURE_SECRET}
                export AZURE_SUBSCRIPTION_ID=${env.AZURE_SUBSCRIPTION_ID}
                export AZURE_TENANT=${env.AZURE_TENANT}
                ansible-playbook azure-teardown.yml --extra-vars "resource_group=${params.resource_group} dr=${params.dr} terminate=${params.terminate} dr_resource_group=${params.dr_resource_group}"
                """
            }
        }

    }


}