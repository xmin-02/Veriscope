from flask import Flask, request, jsonify
from flask_cors import CORS
import sqlite3
import hashlib
import secrets
import re
from datetime import datetime, timedelta

app = Flask(__name__)
CORS(app)  # 모든 도메인에서의 접근 허용

DATABASE_PATH = 'database/veriscope.db'

def get_db_connection():
    """데이터베이스 연결을 반환합니다."""
    conn = sqlite3.connect(DATABASE_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def validate_email(email):
    """이메일 형식을 검증합니다."""
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return re.match(pattern, email) is not None

def hash_password(password):
    """비밀번호를 해시화합니다."""
    return hashlib.sha256(password.encode()).hexdigest()

@app.route('/api/auth/login', methods=['POST'])
def login():
    """사용자 로그인 API"""
    try:
        data = request.get_json()
        
        if not data or 'email' not in data or 'password' not in data:
            return jsonify({
                'success': False,
                'message': '이메일과 비밀번호를 입력해주세요.'
            }), 400
        
        email = data['email'].strip()
        password = data['password'].strip()
        
        if not validate_email(email):
            return jsonify({
                'success': False,
                'message': '올바른 이메일 형식을 입력해주세요.'
            }), 400
        
        conn = get_db_connection()
        user = conn.execute(
            'SELECT id, name, email, password FROM users WHERE email = ?',
            (email,)
        ).fetchone()
        
        if user and user['password'] == hash_password(password):
            conn.close()
            return jsonify({
                'success': True,
                'message': '로그인 성공',
                'data': {
                    'id': user['id'],
                    'name': user['name'],
                    'email': user['email']
                }
            }), 200
        else:
            conn.close()
            return jsonify({
                'success': False,
                'message': '이메일 또는 비밀번호가 잘못되었습니다.'
            }), 401
            
    except Exception as e:
        return jsonify({
            'success': False,
            'message': '서버 오류가 발생했습니다.'
        }), 500

@app.route('/api/auth/signup', methods=['POST'])
def signup():
    """사용자 회원가입 API"""
    try:
        data = request.get_json()
        
        if not data or 'name' not in data or 'email' not in data or 'password' not in data:
            return jsonify({
                'success': False,
                'message': '모든 필드를 입력해주세요.'
            }), 400
        
        name = data['name'].strip()
        email = data['email'].strip()
        password = data['password'].strip()
        
        if len(password) < 6:
            return jsonify({
                'success': False,
                'message': '비밀번호는 6자 이상이어야 합니다.'
            }), 400
        
        if not validate_email(email):
            return jsonify({
                'success': False,
                'message': '올바른 이메일 형식을 입력해주세요.'
            }), 400
        
        conn = get_db_connection()
        
        # 이메일 중복 확인
        existing_user = conn.execute(
            'SELECT id FROM users WHERE email = ?',
            (email,)
        ).fetchone()
        
        if existing_user:
            conn.close()
            return jsonify({
                'success': False,
                'message': '이미 사용중인 이메일입니다.'
            }), 409
        
        # 사용자 생성
        cursor = conn.execute(
            'INSERT INTO users (name, email, password) VALUES (?, ?, ?)',
            (name, email, hash_password(password))
        )
        
        user_id = cursor.lastrowid
        conn.commit()
        conn.close()
        
        return jsonify({
            'success': True,
            'message': '회원가입이 완료되었습니다.',
            'data': {
                'id': user_id,
                'name': name,
                'email': email
            }
        }), 201
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': '서버 오류가 발생했습니다.'
        }), 500

@app.route('/api/auth/forgot-password', methods=['POST'])
def forgot_password():
    """비밀번호 찾기 API"""
    try:
        data = request.get_json()
        
        if not data or 'email' not in data:
            return jsonify({
                'success': False,
                'message': '이메일을 입력해주세요.'
            }), 400
        
        email = data['email'].strip()
        
        if not validate_email(email):
            return jsonify({
                'success': False,
                'message': '올바른 이메일 형식을 입력해주세요.'
            }), 400
        
        conn = get_db_connection()
        user = conn.execute(
            'SELECT id FROM users WHERE email = ?',
            (email,)
        ).fetchone()
        
        if user:
            # 실제로는 이메일 전송 로직 구현 필요
            # 여기서는 임시로 성공 응답만 반환
            reset_token = secrets.token_urlsafe(32)
            expires_at = datetime.now() + timedelta(hours=1)
            
            conn.execute(
                'UPDATE users SET reset_token = ?, reset_token_expires = ? WHERE email = ?',
                (reset_token, expires_at.isoformat(), email)
            )
            conn.commit()
        
        conn.close()
        
        # 보안상 이메일 존재 여부와 관계없이 같은 응답 반환
        return jsonify({
            'success': True,
            'message': '비밀번호 재설정 링크를 이메일로 전송했습니다.'
        }), 200
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': '서버 오류가 발생했습니다.'
        }), 500

@app.route('/api/users', methods=['GET'])
def get_users():
    """사용자 목록 조회 (개발용)"""
    try:
        conn = get_db_connection()
        users = conn.execute(
            'SELECT id, name, email, created_at FROM users ORDER BY created_at DESC'
        ).fetchall()
        conn.close()
        
        user_list = []
        for user in users:
            user_list.append({
                'id': user['id'],
                'name': user['name'],
                'email': user['email'],
                'created_at': user['created_at']
            })
        
        return jsonify({
            'success': True,
            'data': user_list
        }), 200
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': '서버 오류가 발생했습니다.'
        }), 500

@app.route('/api/health', methods=['GET'])
def health_check():
    """서버 상태 확인"""
    return jsonify({
        'success': True,
        'message': 'Veriscope API Server is running',
        'timestamp': datetime.now().isoformat()
    }), 200

if __name__ == '__main__':
    print("🚀 Veriscope API 서버를 시작합니다...")
    print("📊 데이터베이스:", DATABASE_PATH)
    print("🌐 서버 주소: http://localhost:5000")
    print("📋 API 엔드포인트:")
    print("   - POST /api/auth/login")
    print("   - POST /api/auth/signup") 
    print("   - POST /api/auth/forgot-password")
    print("   - GET /api/users (개발용)")
    print("   - GET /api/health")
    
    app.run(debug=True, host='0.0.0.0', port=5000)