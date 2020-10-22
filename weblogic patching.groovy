---
- hosts: servers 
  tasks:
    - name: check new version of WLbinary
      shell: "ls /opt/weblogic | grep wl122 | grep -v oraInventory | tail -1"
      register: version_file
    - debug: msg="{{version_file.stdout}}"
    
    - name: WLhome_path
      shell: echo `cd /opt/fedex/{{ dir }}/stat/wl122*/{{ dir }}/ && pwd`
      register: WLhome_path
 
    - name: Create a symbolic link in weblogic home path
      file:
        src: "/opt/weblogic/{{version_file.stdout}}"
        dest: "{{ WLhome_path.stdout }}/wl_latest"
        state: link
      notify:
        - bounce servers
        
    - name: check new version of Java binary
      shell: "ls /opt/java/hotspot/8/64_bit | tail -1"
      register: java_version_file
    - debug: msg="{{java_version_file.stdout}}"
    
    - name: Create a symbolic link for Java binary
      file:
        src: "/opt/java/hotspot/8/64_bit/{{java_version_file.stdout}}"
        dest: "/opt/java/hotspot/8/latest"
        state: link
      notify:
        - bounce servers
    
    - name: latest java timestamp
      shell: "/usr/bin/stat --format=$'\x25'Y /opt/java/hotspot/8/latest"
      register: java_latest_time
    - debug: msg="{{java_latest_time.stdout}}"
    
    - name: list out managed server
      shell: "ls -l /opt/fedex/{{ dir }}/stat/wl122*/{{ dir }}/servers/ | awk '{print $NF}' | grep Server | grep -v AdminServer"
      register: managed_servers
    - debug: msg="{{managed_servers.stdout}}"
  
    - set_fact:
        manage_server_list: "{{managed_servers.stdout.split('\n')}}"
     
    - name: managed servers timestamps
      shell: "ps -ef | grep {{item}} | tail -1 | awk '{print $2 $3 $5 $4}' | date +%s"
      register: pid_timestamps
      with_items: "{{manage_server_list}}"
    - debug:
        msg: "{{item.stdout}}"
      loop: "{{pid_timestamps.results}}"

    - name: compare time stamps and bounce
      shell: echo "compare timestamps"
      with_items: "{{pid_timestamps.results}}"
      when: java_latest_time.stdout > item.stdout
      notify:
       - bounce servers
       
  handlers:
  
    - name: list out server and stop
      shell: "ls -l /opt/fedex/{{ dir }}/stat/wl122*/{{ dir }}/servers/ | awk '{print $NF}' | grep Server | grep -v AdminServer"
      register: servers
      listen: bounce servers
    - debug: msg="{{servers.stdout}}"
      listen: bounce servers
    - set_fact:
        server_list: "{{servers.stdout.split('\n')}}"
      listen: bounce servers
   
    - name: print manage servers
      shell: echo "{{item}}"
      with_items: "{{server_list}}"
      listen: bounce servers
 
    - name: Executing a script to stop managed servers
      shell: "/opt/fedex/{{ dir }}/stat/wl122*/{{ dir }}/manageWL.sh stop {{item | regex_replace('^(.+)([0-9]{2})$','\\1')}}"
      with_items: "{{server_list}}"
      async: 400
      poll: 0
      listen: bounce servers
  
    - name: sleep 300 seconds
      shell: "sleep 300"
      listen: bounce servers
    
    - name: Executing a script to start managed servers
      shell: "{{ WLhome_path.stdout }}/manageWL.sh start {{item | regex_replace('^(.+)([0-9]{2})$','\\1')}}"
      with_items: "{{server_list}}"
      async: 800
      poll: 0
      listen: bounce servers
        
    - name: list out admin server
      shell: "ls /opt/fedex/{{ dir }}/stat/wl122*/{{ dir }}/servers/ | awk '{print $NF}' | grep AdminServer"
      register: admin_servers
      listen: bounce servers
    - debug: msg="{{admin_servers.stdout}}"
      listen: bounce servers
    - set_fact:
        admin_server_list: "{{admin_servers.stdout.split('\n')}}"
      listen: bounce servers
 
    - name: sleep 300 seconds
      shell: "sleep 300"
      listen: bounce servers

    - name: Executing a script to stop admin servers
      shell: "{{ WLhome_path.stdout }}/manageWL.sh stop {{item}}"
      with_items: "{{admin_server_list}}"
      async: 400
      poll: 0
      listen: bounce servers

    - name: sleep 300 seconds
      shell: "sleep 300"
      listen: bounce servers

    - name: Executing a script to start admin servers
      shell: "{{ WLhome_path.stdout }}/manageWL.sh start {{item}}"
      with_items: "{{admin_server_list}}"
      async: 800
      poll: 0
      listen: bounce servers