#!/usr/bin/python
# Ansible module to modify application security groups on an existing Azure network interface.
# Author: Paul Thomas
# Date: March 2019

ANSIBLE_METADATA = {'metadata_version': '1.1',
                    'status': ['preview'],
                    'supported_by': 'community'}

DOCUMENTATION = '''
---
module: azure_rm_networkinterface_asg.py
short_description: Adds application security groups support for the Ansible Azure network interface module.
description:
  - Adds application security groups support for the Ansible Azure network interface module.
  - Not required as from Ansible 2.8 where application support group is included in the official module.
  - Interface remains the same as for the official implementation to ease replacement.
version_added: "2.7"
author: Paul Thomas
options:
  subscription_id:
    description: Azure subscription id.
    required: true
    type: str
  client_id:
    description: Azure client id.
    required: true
    type: str
  secret:
    description: Azure client secret.
    required: true
    type: str
  tenant_id:
    description: Azure tenant id.
    required: true
    type: str
  resource_group:
    description: Name of the resource group that the network interface to be modified belongs to.
    required: true
    type: str
  name:
    description: Name of the network interface to be modified.
    required: true
    type: str
  ip_configurations:
      description:
          - List of ip configuration if contains mutiple configuration, should contain configuration object include
            field private_ip_address, private_ip_allocation_method, public_ip_address_name, public_ip, public_ip_allocation_method, name
      suboptions:
          name:
              description:
                  - Name of the ip configuration.
              required: true
          application_security_groups:
              description:
                  - List of application security groups in which the IP configuration is included.
                  - Element of the list could be a resource id of application security group, or dict of C(resource_group) and C(name)    
requirements:
  - python >= 2.7
  - azure >=2.0.0
'''

EXAMPLES = '''
- name: Update application security groups on a single ip configuration
  azure_rm_networkinterface_asg:
    subscription_id: '**********'
    client_id: '**********'
    secret: '**********'
    tenant: '**********'
    resource_group: my-resource-group
    name: my-network-interface
    ip_configurations:
      - name: ipconfig0
        application_security_groups:
          - name: my-asg-1
            resource_group: my-resource-group
          - name: my-other-asg
            resource_group: my-other-resource_group
- name: Add application security group to multiple ip configurations
  azure_rm_networkinterface_asg:
    subscription_id: '**********'
    client_id: '**********'
    secret: '**********'
    tenant: '**********'
    resource_group: my-resource-group
    name: my-network-interface
    ip_configurations:
      - name: ipconfig0
        application_security_groups:
          - name: my-asg-1
            resource_group: my-resource-group
          - name: my-other-asg
            resource_group: my-other-resource_group
      - name: ipconfig1
        application_security_groups:
          - name: my-asg-1
            resource_group: my-resource-group
          - name: my-other-asg
            resource_group: my-other-resource_group
      - name: ipconfig2
        application_security_groups:
          - name: my-asg-1
            resource_group: my-resource-group
          - name: my-other-asg
            resource_group: my-other-resource_group
'''

RETURN = ''' # '''

from ansible.module_utils.basic import AnsibleModule

from azure.common.credentials import ServicePrincipalCredentials
from azure.mgmt.network import NetworkManagementClient
from azure.mgmt.network.models import ApplicationSecurityGroup

import json

class AzureClient:
  """Abstracts all interactions with Azure."""

  def authenticate(self, client_id, secret, tenant, subscription_id):
      """Authenticate with Azure."""
      self.credentials = ServicePrincipalCredentials(client_id=client_id, secret=secret, tenant=tenant)
      self.client = NetworkManagementClient(self.credentials, subscription_id)

  def get_network_interface(self, resource_group, name):
      """Call Azure to get network interface object."""
      return self.client.network_interfaces.get(resource_group, name)

  def update_network_interface(self, resource_group, name, network_interface):
      """Call Azure to update nework interface object.
      Returns dict containing failed boolean and optional message.
      """
      poller = self.client.network_interfaces.create_or_update(resource_group, name, network_interface)
      while True:
          poller.result(timeout=30)
          if poller.done():
              break
      if poller.status().lower() != 'succeeded':
        return {"failed": True, "message": poller.status()}
      return {"failed": False}

def _build_application_security_group_id(subscription_id, resource_group_name, application_security_group_name):
    """Builds an application security group id given a subscription id, resource group name and application security group name."""
    return '/subscriptions/' + subscription_id + '/resourceGroups/' + resource_group_name + '/providers/Microsoft.Network/applicationSecurityGroups/' + application_security_group_name

def _application_security_groups_contains(application_security_groups, application_security_group_id):
    """Returns true if the given application security group name is present in the application security groups."""
    if application_security_groups is None:
        return False

    for application_security_group in application_security_groups:
        if application_security_group.id.lower() == application_security_group_id.lower():
            return True

    return False

def _application_security_groups_to_id_list(subscription_id, application_security_groups):
    """Converts a list of application security group dicts (containing name and resource_group) into a list of asg ids."""
    application_security_group_ids = []
    for application_security_group in application_security_groups:
        application_security_group_ids.append(_build_application_security_group_id(subscription_id, application_security_group['resource_group'], application_security_group['name']))
    return application_security_group_ids

def _modify_ip_configuration(ip_configuration, application_security_group_ids):
    """Modify a given ip_configuration with an updated list of application security group ids.
    Returns True if the ip configuration was changed, otherwise returns False.
    """

    changed = False

    # Remove all groups that don't match the required groups
    if ip_configuration.application_security_groups is not None:
        list_len = len(ip_configuration.application_security_groups)
        ip_configuration.application_security_groups = [asg for asg in ip_configuration.application_security_groups if asg.id in application_security_group_ids]
        changed = list_len != len(ip_configuration.application_security_groups)

    # Add any new groups
    for application_security_group_id in application_security_group_ids:
        if ip_configuration.application_security_groups is None:
            # Create a new list
            ip_configuration.application_security_groups = [ApplicationSecurityGroup(id=unicode(application_security_group_id))]
            changed = True
        else:
            if not _application_security_groups_contains(ip_configuration.application_security_groups, application_security_group_id):
                # Add ASG to existing list
                ip_configuration.application_security_groups.append(ApplicationSecurityGroup(id=unicode(application_security_group_id)))
                changed = True    
    return changed

def _modify_network_interface(network_interface, ip_configuration_name, application_security_group_ids):
    """Modify network interface data for given ip_configuration and list of application security group ids.
    Returns Boolean indicating whether the data was changed.
    """

    changed = False
    for ip_configuration in network_interface.ip_configurations:
        if ip_configuration_name is None or ip_configuration_name.lower() == ip_configuration.name.lower():
            if _modify_ip_configuration(ip_configuration, application_security_group_ids):
                changed = True
    return changed

def _validate_ip_configurations(ip_configurations):
    """Validate ip_configurations data passed into module.
    Returns boolean indicating True if the data is valid, otherwise False.
    Where the data is invalid, an error message is returned.
    """
    for ip_configuration in ip_configurations:
        
        if ip_configuration.get('name') is None:
            return False, "ip_configuration must contain property: name"
        
        if ip_configuration.get('application_security_groups') is not None:
            for asg in ip_configuration['application_security_groups']:
                if asg.get('name') is None:
                    return False, "application_security_group must contain property: name"
                if asg.get('resource_group') is None:
                    return False, "application_security_group must contain property: resource_group"
    return True, None

def _update_application_security_groups(client_id, secret, tenant, subscription_id, resource_group, name, ip_configurations):
    """Update application security groups on an Azure network interface.
    Reads current state from cloud, makes changes and updates the cloud if necessary.
    Returns error, changed, message
        error - True if an error occurred, otherwise false
        changed - True if the network interface state was changed, otherwise false
        message - error message in case of error
    """
    changed = False

    try:
        client = AzureClient()
        client.authenticate(client_id, secret, tenant, subscription_id)
        network_interface = client.get_network_interface(resource_group, name)

        # Update network interface object data.
        for ip_configuration in ip_configurations:
            if ip_configuration.get('application_security_groups') is not None:
                changed = _modify_network_interface(network_interface, ip_configuration['name'], _application_security_groups_to_id_list(subscription_id, ip_configuration['application_security_groups']))

        # If changes were made, update the cloud.   
        if changed:
            result = client.update_network_interface(resource_group, name, network_interface)
            if result['failed'] == True:
                return True, False, result['message']

    except Exception, e:
        return True, False, e.message

    return False, changed, None

def run_module(module):
    """Run the module.
    Returns:
      failed - True if failed, otherwise false, otherwise false
      changed - True if the state has changed, otherwise false 
      message - Message in the event of failure
    """
    valid, message = _validate_ip_configurations(module.params['ip_configurations'])
    if not valid:
        return True, { "changed": False }, message

    # Execute module
    failed, changed, message = _update_application_security_groups(
        subscription_id=module.params['subscription_id'],
        client_id=module.params['client_id'],
        secret=module.params['secret'],
        tenant=module.params['tenant'],
        resource_group=module.params['resource_group'],
        name=module.params['name'],      
        ip_configurations=module.params['ip_configurations']
    )

    return failed, { "changed": changed }, message
    
def main():
    """Module main function."""
    
    # define available arguments/parameters a user can pass to the module
    module_args = dict(
        subscription_id=dict(type='str', required=True),
        client_id=dict(type='str', required=True),
        secret=dict(type='str', required=True, no_log=True),
        tenant=dict(type='str', required=True),
        resource_group=dict(type='str', required=True),
        name=dict(type='str', required=True),
        ip_configurations=dict(type='list', required=True)
    )

    # seed the result dict in the object
    # we primarily care about changed and state
    # change is if this module effectively modified the target
    # state will include any data that you want your module to pass back
    # for consumption, for example, in a subsequent task
    result = dict(
        changed=False
    )

    # the AnsibleModule object will be our abstraction working with Ansible
    # this includes instantiation, a couple of common attr would be the
    # args/params passed to the execution, as well as if the module
    # supports check mode
    module = AnsibleModule(
        argument_spec=module_args,
        supports_check_mode=True
    )

    # if the user is working with this module in only check mode we do not
    # want to make any changes to the environment, just return the current
    # state with no modifications
    if module.check_mode:
        return result

    # Validate ip_configurations
    failed, result, message = run_module(module)

    if failed:
      module.fail_json(msg=message, **result)

    module.exit_json(**result)

if __name__ == '__main__':
    main()