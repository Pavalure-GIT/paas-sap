pipeline{
    agent any

    parameters{
      string defaultValue: 'vpc name', description: 'Enter the VPC Name here', name: 'st_vpc_name', trim: true
      string defaultValue: 'vpc-0944347d3badefgte', description: 'Enter the VPC ID here', name: 'st_vpc_id', trim: true
      string defaultValue: 'region name', description: 'Enter the region Name here', name: 'st_region', trim: true
      choice choices: ['true', 'false'], description: '', name: 'st_list'
      choice choices: ['true', 'false'], description: '', name: 'st_terminate'
      choice choices: ['false', 'true'], description: '', name: 'st_requester'
      string defaultValue: 'xx.aws.paas4sap.svcs.entsvcs.com', description: 'enter vpc short name. Value can be found with the vars file used to build the VPC or use the VPC-ID to find shortname in route53', name: 'vpc_shortname', trim: true
      string defaultValue: 'Z19T3FXREVQ890', description: 'enter vpc short name id. Use the VPC-ID to find shortname ID in route53', name: 'vpc_shortname_id', trim: true
      string defaultValue: 'xxxxxx.paas4sap.svcs.entsvcs.com', description: 'enter vpc long name. Value can be found with the vars file used to build the VPC or use the VPC-ID to find longname in route53', name: 'vpc_longname', trim: true
      string defaultValue: 'Z19T3FXREVEDR9', description: 'enter vpc long name id. Use the VPC-ID to find longname ID in route53', name: 'vpc_longname_id', trim: true
      string defaultValue: 'Z19T3DERRVEDR9', description: 'in-addr.arpa VPC-ID to find longname ID in route53', name: 'in_addr_arpa', trim: true
      string defaultValue: '-', description: 'virtual private gateway name', name: 'vgw_name', trim: true
      string defaultValue: 'vgw-0f73c280059aa00d2', description: 'virtual private gateway ID', name: 'gateway_id', trim: true
      choice choices: ['transitvpc:spoke','-'], description: 'The CSR tag for the virtual private gateway', name: 'csr_tag'
      string defaultValue: '-', description: 'Name of the CSR stack', name: 'stack_name', trim: true
      choice choices: ['false', 'true'], description: 'tear down DR', name: 'dr'
      string defaultValue: 'vpc-0af9bf39247934d58', description: 'Enter the DR VPC ID', name: 'dr_vpc_id', trim: true
      string defaultValue: 'DR-Dev-Example', description: 'Enter the VPC Name here', name: 'dr_vpc_name', trim: true
      string defaultValue: 'us-east-2', description: 'Enter the region here', name: 'dr_region', trim: true
      choice choices: ['false','true'], description: 'dr list', name: 'dr_list'
      choice choices: ['false', 'true'], description: 'dr terminate', name: 'dr_terminate'
      

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
                git url: 'e4s@vs-ssh.visualstudio.com:v3/e4s/E4S-PublicCloud/SystemTeam', credentialsId: 'p4s-engineering', branch:'teardown'
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
    "st_vpc":"${params.st_vpc_id}",
	"st_region":"${params.st_region}",
	"list":"${params.st_list}",
	"terminate":"${params.st_terminate}",
	"requester":"${params.st_requester}",
	"route53_zones": {
		"${params.vpc_shortname}": "${params.vpc_shortname_id}",
		"${params.vpc_longname}": "${params.vpc_longname_id}",
		"in-addr.arpa.": "${params.in_addr_arpa}"
	},
	"st_vpc_name": "${params.st_vpc_name}",
    "vgw_name": "${params.vgw_name}",
	"gateway_id": "${params.gateway_id}",
    "csr_tag": "${params.csr_tag}",
    "st_name": "${params.stack_name}",
	"dr": "${params.dr}",
	"dr_vpc_id": "${params.dr_vpc_id}",
	"dr_vpc_name": "${params.dr_vpc_name}",
	"dr_region": "${params.dr_region}",
	"dr_list": "${params.dr_list}",
	"dr_terminate": "${params.dr_terminate}"
}'
    """
            }
        }
    }

    
}
