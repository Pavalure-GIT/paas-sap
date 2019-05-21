pipeline{
    agent any

    parameters{
      string defaultValue: 'subscription', description: 'enter aws subscription where compartment lives', name: 'subscription', trim: true
      string defaultValue: 'vpc name', description: 'Enter the VPC Name here', name: 'vpc_name', trim: true
      string defaultValue: 'vpc-0944347d3badefgte', description: 'Enter the VPC ID here', name: 'vpc_id', trim: true
      string defaultValue: 'region name', description: 'Enter the region Name here', name: 'region', trim: true
      choice choices: ['true', 'false'], description: '', name: 'list'
      choice choices: ['true', 'false'], description: '', name: 'terminate'
      choice choices: ['false', 'true'], description: '', name: 'requester'
      string defaultValue: 'xx.aws.paas4sap.svcs.entsvcs.com', description: 'enter vpc short name. Value can be found with the vars file used to build the VPC or use the VPC-ID to find shortname in route53', name: 'vpc_shortname', trim: true
      string defaultValue: 'Z19T3FXREVQ890', description: 'enter vpc short name id. Use the VPC-ID to find shortname ID in route53', name: 'vpc_shortname_id', trim: true
      string defaultValue: 'xxxxxx.aws.paas4sap.svcs.entsvcs.com', description: 'enter vpc long name. Value can be found with the vars file used to build the VPC or use the VPC-ID to find longname in route53', name: 'vpc_longname', trim: true
      string defaultValue: 'Z19T3FXREVEDR9', description: 'enter vpc long name id. Use the VPC-ID to find longname ID in route53', name: 'vpc_longname_id', trim: true
      string defaultValue: 'Z19T3DERRVEDR9', description: 'in-addr.arpa VPC-ID to find longname ID in route53', name: 'in_addr_arpa', trim: true

    }
     //get keys from vault
    environment {
                AWS_ACCESS_KEY = vault path: "secret/${params.subscription}/automation/aws/access_key", key: 'value', vaultUrl: 'http://10.165.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
                AWS_SECRET_KEY = vault path: "secret/${params.subscription}/automation/aws/secret_key", key: 'value', vaultUrl: 'http://10.165.0.11:8200', credentialsId: 'jenkinsreadvaulttoken'
                AWS_DEFAULT_REGION = 'us-east-1'
            }

    
 
    stages{
        stage('clone git repo'){
            steps{
                git url: 'e4s@vs-ssh.visualstudio.com:v3/e4s/E4S-PublicCloud/SystemTeam', credentialsId: 'daivdprivatekeygit'
            }
         
        }
        
        stage('Tear down AWS'){
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
                ansible-playbook aws-teardown.yml --extra-vars '{
	"vpc":"${params.vpc_id}",
	"region":"${params.region}",
	"list":"${params.list}",
	"terminate":"${params.terminate}",
	"requester":"${params.requester}",
	"route53_zones": {
		"${params.vpc_shortname}": "${params.vpc_shortname_id}",
		"${params.vpc_longname}": "${params.vpc_longname_id}",
		"in-addr.arpa.": "${params.in_addr_arpa}"
	},
	"vpc_name": "${params.vpc_name}"
}'
                """
            }
        }

    }

    
}