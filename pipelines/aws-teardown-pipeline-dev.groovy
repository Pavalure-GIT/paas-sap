pipeline{
    agent any

    parameters{
      string defaultValue: '---', description: 'Enter config file id', name: 'teardown_config', trim: true
    }
    //get keys from vault
    environment {
        AWS_ACCESS_KEY = vault path: 'secret/720232237161/automation/aws/access_key', key: 'value', vaultUrl: 'http://10.4.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
        AWS_SECRET_KEY = vault path: 'secret/720232237161/automation/aws/secret_key', key: 'value', vaultUrl: 'http://10.4.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
        AWS_DEFAULT_REGION = 'us-east-1'
    }
    
 
    stages{
        stage('clone git repo'){
            steps{
                git url: 'e4s@vs-ssh.visualstudio.com:v3/e4s/E4S-PublicCloud/SystemTeam', credentialsId: 'daivdprivatekeygit', branch: 'david-aws-teardown-fix'
            }
         
        }
        
        stage('Tear down AWS'){
                        //stop logging, export env variable for aws cli in virutal env, pull down vars file and kick off build
            steps{

                configFileProvider([configFile(fileId: "${params.teardown_config}", targetLocation: "group_vars/${params.teardown_config}.yml")]) {
                }
                sh """
                set +x
                virtualenv .
                source bin/activate
                pip install -r aws_requirements.txt
                export AWS_ACCESS_KEY_ID=${env.AWS_ACCESS_KEY}
                export AWS_SECRET_ACCESS_KEY=${env.AWS_SECRET_KEY}
                export AWS_DEFAULT_REGION=${env.AWS_DEFAULT_REGION}
                ansible-playbook aws-teardown.yml --extra-vars @group_vars/${params.teardown_config}.yml
                """
            }
        }
    }

    
}
