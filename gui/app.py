#!/usr/bin/env python3
"""
DNS Management Web GUI
Simple Flask application for managing DNS records
Localhost only (secure by default)
"""

from flask import Flask, render_template, request, jsonify, redirect, url_for
import subprocess
import os

app = Flask(__name__)

# Configuration
DNS_CLI = os.path.join(os.path.dirname(__file__), '..', 'dns')
BASE_DIR = os.path.dirname(__file__)

def run_dns_command(command):
    """Execute DNS CLI command and return output"""
    try:
        result = subprocess.run(
            [DNS_CLI] + command,
            capture_output=True,
            text=True,
            timeout=10
        )
        return {
            'success': result.returncode == 0,
            'output': result.stdout,
            'error': result.stderr
        }
    except Exception as e:
        return {
            'success': False,
            'output': '',
            'error': str(e)
        }

def parse_dns_list():
    """Parse DNS list output into structured data"""
    result = run_dns_command(['list'])
    if not result['success']:
        return []
    
    records = []
    lines = result['output'].strip().split('\n')
    
    # Skip header and separator
    for line in lines[2:]:
        parts = line.split()
        if len(parts) >= 3:
            hostname = parts[0]
            record_type = parts[1]
            value = parts[2]
            records.append({
                'hostname': hostname,
                'type': record_type,
                'value': value
            })
    
    return records

@app.route('/')
def index():
    """Main dashboard"""
    records = parse_dns_list()
    return render_template('index.html', records=records)

@app.route('/api/records')
def api_records():
    """API endpoint to get all records"""
    records = parse_dns_list()
    return jsonify(records)

@app.route('/api/add', methods=['POST'])
def api_add():
    """API endpoint to add a record"""
    data = request.get_json()
    hostname = data.get('hostname', '').strip()
    ip = data.get('ip', '').strip()
    
    if not hostname or not ip:
        return jsonify({'success': False, 'error': 'Hostname and IP required'}), 400
    
    # Use 'set' command to add or update
    result = run_dns_command(['set', hostname, ip, '--force'])
    
    if result['success']:
        return jsonify({'success': True, 'message': f'Record {hostname} added/updated'})
    else:
        return jsonify({'success': False, 'error': result['error']}), 500

@app.route('/api/delete', methods=['POST'])
def api_delete():
    """API endpoint to delete a record"""
    data = request.get_json()
    hostname = data.get('hostname', '').strip()
    
    if not hostname:
        return jsonify({'success': False, 'error': 'Hostname required'}), 400
    
    # Use delete command with --force to skip confirmation
    result = run_dns_command(['delete', hostname, '--force'])
    
    if result['success']:
        return jsonify({'success': True, 'message': f'Record {hostname} deleted'})
    else:
        return jsonify({'success': False, 'error': result['error']}), 500

@app.route('/api/reload', methods=['POST'])
def api_reload():
    """API endpoint to reload DNS zone"""
    result = run_dns_command(['reload'])
    
    if result['success']:
        return jsonify({'success': True, 'message': 'Zone reloaded successfully'})
    else:
        return jsonify({'success': False, 'error': result['error']}), 500

@app.route('/api/status')
def api_status():
    """API endpoint to get DNS server status"""
    result = run_dns_command(['status'])
    
    return jsonify({
        'success': result['success'],
        'output': result['output'],
        'error': result['error']
    })

if __name__ == '__main__':
    print("=" * 60)
    print("DNS Management Web GUI")
    print("=" * 60)
    print(f"Access at: http://127.0.0.1:8080")
    print("Press Ctrl+C to stop")
    print("=" * 60)
    
    # Run on localhost only (secure by default)
    app.run(host='127.0.0.1', port=8080, debug=True)
