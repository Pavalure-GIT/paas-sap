pipeline{
    agent any
    
    parameters {
        choice choices: ['AWS', 'AZURE'], description: 'Select the cloud provider to host the compartment', name: 'cloud_provider'
    }


    
 
    stages{
        stage('Check Out SCM'){
            steps{
                git url: 'e4s@vs-ssh.visualstudio.com:v3/e4s/E4S-PublicCloud/SystemTeam', credentialsId: 'daivdprivatekeygit', branch: 'davidasg-refactor'
            }
         
        }
        
        stage('AWS'){
            //check if aws was selected in the build parameter
            when{
                expression { params.cloud_provider == "AWS" }
            }
            //get keys from vault
            environment {
                AWS_ACCESS_KEY = vault path: 'secret/720232237161/automation/aws/access_key', key: 'value', vaultUrl: 'http://10.4.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
                AWS_SECRET_KEY = vault path: 'secret/720232237161/automation/aws/secret_key', key: 'value', vaultUrl: 'http://10.4.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
                AWS_DEFAULT_REGION = 'us-east-1'
            }

            //stop logging, export env variable for aws cli in virutal env, pull down vars file and kick off build
            steps{
                sh """
                set +x
                virtualenv .
                source bin/activate
                pip install -r aws_requirements.txt
                export AWS_ACCESS_KEY_ID=${env.AWS_ACCESS_KEY}
                export AWS_SECRET_ACCESS_KEY=${env.AWS_SECRET_KEY}
                export AWS_DEFAULT_REGION=${env.AWS_DEFAULT_REGION}
                aws s3 cp s3://jenkins-vars/vars/${params.vars_file} group_vars/
                ansible-playbook aws-customer-full.yml --extra-vars @group_vars/${params.vars_file}
                
                """
            }
        }


        stage('Azure'){
            when{
                expression { params.cloud_provider == "AZURE" }
            }
             //get keys from vault
            environment {
            AZURE_CLIENT_ID = vault path: 'secret/128a9e84-5963-4862-a4d5-8fbc804c56cb/automation/azure/client_id', key: 'value', vaultUrl: 'http://10.4.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
            AZURE_SECRET = vault path: 'secret/128a9e84-5963-4862-a4d5-8fbc804c56cb/automation/azure/client_secret', key: 'value', vaultUrl: 'http://10.4.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
            AZURE_SUBSCRIPTION_ID = vault path: 'secret/128a9e84-5963-4862-a4d5-8fbc804c56cb/automation/azure/subscription_id', key: 'value', vaultUrl: 'http://10.4.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
            AZURE_TENANT = vault path: 'secret/128a9e84-5963-4862-a4d5-8fbc804c56cb/automation/azure/tenant_id', key: 'value', vaultUrl: 'http://10.4.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
            
            }

            configFileProvider([configFile(fileId: 'azure_cust.yml', targetLocation: 'group_vars/azure_cust.yml')]) {
                // some block
            }
            
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
                ansible-playbook azure-customer-full.yml
                """
            }
        }

    }

    
}

