import os

def parse_config(config_path):
    with open(config_path, 'r') as f:
        data = f.read().strip().split()
        num_messages = int(data[0])
        receiver_process_id = int(data[1])
    return num_messages, receiver_process_id

def parse_hosts(hosts_path):
    hosts = {}
    with open(hosts_path, 'r') as f:
        for line in f:
            host_id, host_ip, host_port = line.strip().split()
            hosts[int(host_id)] = (host_ip, int(host_port))
    return hosts

def parse_proc_file(proc_file, receiver_process_id):
    sent_messages = set()
    delivered_messages = {}

    with open(proc_file, 'r') as f:
        for line in f:
            parts = line.strip().split()
            if parts[0] == 'b':
                message_nonce = int(parts[1])
                sent_messages.add(message_nonce)
            elif parts[0] == 'd':
                sender_id = int(parts[1])
                message_nonce = int(parts[2])
                if sender_id not in delivered_messages:
                    delivered_messages[sender_id] = set()
                delivered_messages[sender_id].add(message_nonce)

    return sent_messages, delivered_messages

def verify_messages(config_path, hosts_path, directory):
    # Parse config and hosts
    num_messages, receiver_process_id = parse_config(config_path)
    hosts = parse_hosts(hosts_path)

    for host_id in hosts:
        proc_file_path = os.path.join(directory, f'proc{host_id:02}.output')
        if not os.path.exists(proc_file_path):
            print(f"File {proc_file_path} not found")
            continue

        sent_messages, delivered_messages = parse_proc_file(proc_file_path, receiver_process_id)

        if host_id == receiver_process_id:
            # Check that receiver delivered all messages from other processes
            for sender_id in hosts:
                if sender_id == receiver_process_id:
                    continue
                if sender_id not in delivered_messages:
                    print(f"Receiver did not deliver any message from process {sender_id}")
                elif len(delivered_messages[sender_id]) != num_messages:
                    print(f"Receiver delivered {len(delivered_messages[sender_id])} messages from process {sender_id}, expected {num_messages}")
        else:
            # Check that sender sent all messages
            if len(sent_messages) != num_messages:
                print(f"Process {host_id} sent {len(sent_messages)} messages, expected {num_messages}")

if __name__ == "__main__":
    # Input: directory path containing 'config', 'hosts' and procXX.output files
    directory_path = input("Enter the directory path: ")

    config_file = os.path.join(directory_path, 'config')
    hosts_file = os.path.join(directory_path, 'hosts')

    verify_messages(config_file, hosts_file, directory_path)
