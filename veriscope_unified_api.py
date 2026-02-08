from flask import Flask, request, jsonify
from flask_cors import CORS
import sqlite3
import hashlib
import secrets
import re
import subprocess
import json
import os
import base64
import tempfile
from datetime import datetime, timedelta

app = Flask(__name__)
CORS(app)  # 모든 도메인에서의 접근 허용

# 데이터베이스 설정
DATABASE_PATH = 'database/veriscope.db'

# Veriscope CLI 설정
PYTHON_PATH = "C:/Smart_IT/.venv/Scripts/python.exe"
SCRIPT_PATH = "C:/Smart_IT/Veriscope.py"

# =============================================================================
# 데이터베이스 유틸리티 함수
# =============================================================================

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

# =============================================================================
# Veriscope CLI 파싱 함수
# =============================================================================

def parse_cli_output(output):
    """CLI 출력 파싱 함수 (테스트된 버전)"""
    reliability_score = None
    reliability_level = None
    evidence_list = []
    recommendation = ""
    
    lines = output.split('\n')
    print(f"[DEBUG] 파싱할 라인 수: {len(lines)}")
    
    for i, line in enumerate(lines):
        line = line.strip()
        
        # 신뢰도 점수 추출
        if "신뢰도:" in line:
            print(f"[DEBUG] 신뢰도 라인 발견: {line}")
            match = re.search(r'신뢰도:\s*(\d+)%\s*-\s*(.+)', line)
            if match:
                reliability_score = int(match.group(1))
                reliability_level = match.group(2).strip()
                print(f"[DEBUG] 파싱 성공: {reliability_score}% - {reliability_level}")
        
        # 근거 자료 추출
        if re.match(r'^\d+\.\s*\d+%\s*:', line):
            print(f"[DEBUG] 근거 라인 발견: {line}")
            evidence_match = re.search(r'^(\d+)\.\s*(\d+)%\s*:\s*(https?://[^\s]+)\s*\(유사성:\s*([\d.]+),\s*지지도:\s*([\d.]+)\)', line)
            if evidence_match:
                evidence_number = int(evidence_match.group(1))
                evidence_list.append({
                    "number": evidence_number,
                    "rank": evidence_number,
                    "score": int(evidence_match.group(2)),
                    "url": evidence_match.group(3),
                    "similarity": float(evidence_match.group(4)),
                    "support": float(evidence_match.group(5))
                })
                print(f"[DEBUG] 근거 파싱 성공")
        
        # 권장사항 추출
        if "권장사항:" in line:
            recommendation = line.split("권장사항:")[-1].strip()
            print(f"[DEBUG] 권장사항: {recommendation}")
    
    return {
        "reliability_score": reliability_score,
        "reliability_level": reliability_level,
        "recommendation": recommendation,
        "evidence_count": len(evidence_list),
        "evidence": evidence_list
    }

# =============================================================================
# 기본 라우트
# =============================================================================

@app.route('/', methods=['GET'])
def home():
    """API 상태 확인"""
    return jsonify({
        "status": "ok",
        "service": "Veriscope - 뉴스 신뢰도 평가 & 사용자 관리 API",
        "version": "2.0",
        "timestamp": datetime.now().isoformat(),
        "description": "통합 API 서버 - 사용자 인증 + 뉴스 신뢰도 평가",
        "endpoints": {
            "auth": {
                "login": "POST /api/auth/login",
                "signup": "POST /api/auth/signup", 
                "forgot-password": "POST /api/auth/forgot-password"
            },
            "evaluation": {
                "evaluate-url": "POST /api/evaluate",
                "evaluate-image": "POST /api/evaluate-image"
            },
            "system": {
                "health": "GET /api/health",
                "users": "GET /api/users"
            }
        }
    })

@app.route('/api/health', methods=['GET'])
def health_check():
    """서버 상태 확인"""
    try:
        # 데이터베이스 연결 테스트
        conn = get_db_connection()
        cursor = conn.execute("SELECT COUNT(*) FROM users")
        user_count = cursor.fetchone()[0]
        conn.close()
        
        # CLI 명령 테스트
        cli_result = subprocess.run([
            PYTHON_PATH, SCRIPT_PATH, "--version"
        ], capture_output=True, text=True, timeout=10, cwd="C:/Smart_IT")
        
        return jsonify({
            "status": "healthy",
            "timestamp": datetime.now().isoformat(),
            "database": {
                "status": "connected",
                "user_count": user_count
            },
            "veriscope_cli": {
                "status": "available" if cli_result.returncode == 0 else "unavailable",
                "python_path": PYTHON_PATH,
                "script_path": SCRIPT_PATH
            }
        }), 200
        
    except Exception as e:
        return jsonify({
            "status": "unhealthy",
            "timestamp": datetime.now().isoformat(),
            "error": str(e)
        }), 500

# =============================================================================
# 사용자 인증 API
# =============================================================================

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
            'message': f'서버 오류: {str(e)}'
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
            'message': f'서버 오류: {str(e)}'
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
            'message': f'서버 오류: {str(e)}'
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
            'message': f'서버 오류: {str(e)}'
        }), 500

# =============================================================================
# 뉴스 신뢰도 평가 API
# =============================================================================

@app.route('/api/evaluate', methods=['POST'])
def evaluate():
    """뉴스 신뢰도 평가"""
    print("[DEBUG] /api/evaluate endpoint called")
    try:
        # 요청 데이터 확인
        if not request.is_json:
            return jsonify({"success": False, "error": "JSON 데이터가 필요합니다."}), 400
            
        data = request.get_json()
        url = data.get('url')
        user_id = data.get('user_id')  # 사용자 ID (로그 용도)
        
        if not url:
            return jsonify({"success": False, "error": "URL이 필요합니다."}), 400
            
        # 옵션 파라미터
        similarity_threshold = data.get('similarity_threshold', 0.6)
        use_gpu = data.get('use_gpu', True)
        fp16 = data.get('fp16', True)
        nli_batch = data.get('nli_batch', 128)
        
        # CLI 명령 구성
        cmd = [
            PYTHON_PATH, SCRIPT_PATH, "evaluate",
            "--url", url,
            "--similarity-threshold", str(similarity_threshold),
            "--nli-batch", str(nli_batch)
        ]
        
        if use_gpu:
            cmd.append("--use-gpu")
        if fp16:
            cmd.append("--fp16")
        
        print(f"실행 명령: {' '.join(cmd)}")
        
        # 평가 시작 알림
        print("📊 뉴스 신뢰도 평가 시작...")
        
        # CLI 실행
        start_time = datetime.now()
        
        # 환경변수 설정으로 유니코드 문제 해결
        env = os.environ.copy()
        env['PYTHONIOENCODING'] = 'utf-8'
        env['PYTHONLEGACYWINDOWSFSENCODING'] = '1'
        
        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=120,  # 2분 타임아웃
                cwd="C:/Smart_IT",
                env=env,
                encoding='utf-8',
                errors='replace'
            )
        except subprocess.TimeoutExpired:
            return jsonify({
                "success": False,
                "error": "CLI 실행 시간 초과 (2분)",
                "elapsed_seconds": 120
            }), 500
        except Exception as e:
            return jsonify({
                "success": False,
                "error": f"CLI 실행 오류: {str(e)}",
                "elapsed_seconds": round((datetime.now() - start_time).total_seconds(), 1)
            }), 500
            
        end_time = datetime.now()
        elapsed = (end_time - start_time).total_seconds()
        
        # 결과 파싱
        if result.returncode == 0:
            output = result.stdout
            print(f"[DEBUG] CLI Output Length: {len(output)}")
            
            parsed_result = parse_cli_output(output)
            
            # 평가 결과를 데이터베이스에 저장 (사용자가 로그인한 경우)
            if user_id and parsed_result.get('reliability_score') is not None:
                try:
                    conn = get_db_connection()
                    conn.execute(
                        'INSERT INTO news_evaluations (user_id, news_url, evaluation_score, evaluation_result) VALUES (?, ?, ?, ?)',
                        (user_id, url, parsed_result['reliability_score'], json.dumps(parsed_result))
                    )
                    conn.commit()
                    conn.close()
                    print(f"[DEBUG] 평가 결과 저장 완료 - 사용자 {user_id}")
                except Exception as e:
                    print(f"[DEBUG] 평가 결과 저장 실패: {e}")
            
            return jsonify({
                "success": True,
                "message": "뉴스 신뢰도 평가 완료",
                "timestamp": datetime.now().isoformat(),
                "elapsed_seconds": round(elapsed, 1),
                "url": url,
                "parameters": {
                    "similarity_threshold": similarity_threshold,
                    "use_gpu": use_gpu,
                    "fp16": fp16,
                    "nli_batch": nli_batch
                },
                "result": parsed_result
            })
        else:
            return jsonify({
                "success": False,
                "error": "CLI 실행 실패",
                "return_code": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr,
                "elapsed_seconds": round(elapsed, 1)
            }), 500
            
    except Exception as e:
        return jsonify({
            "success": False,
            "error": f"요청 처리 오류: {str(e)}",
            "timestamp": datetime.now().isoformat()
        }), 500

@app.route('/api/evaluate-image', methods=['POST'])
def evaluate_image():
    """이미지 신뢰도 평가"""
    print("[DEBUG] /api/evaluate-image endpoint called")
    try:
        # 요청 데이터 확인
        if not request.is_json:
            return jsonify({"success": False, "error": "JSON 데이터가 필요합니다."}), 400
            
        data = request.get_json()
        
        # Base64 인코딩된 이미지 데이터 또는 이미지 파일 경로
        image_data = data.get('image_data')
        image_path = data.get('image_path')
        user_id = data.get('user_id')
        
        if not image_data and not image_path:
            return jsonify({"success": False, "error": "image_data 또는 image_path가 필요합니다."}), 400
        
        # 옵션 파라미터
        similarity_threshold = data.get('similarity_threshold', 0.5)
        ocr_method = data.get('ocr_method', 'easyocr')
        use_gpu = data.get('use_gpu', True)
        fp16 = data.get('fp16', True)
        nli_batch = data.get('nli_batch', 32)
        
        temp_image_path = None
        
        try:
            # Base64 이미지 데이터 처리
            if image_data:
                try:
                    if ',' in image_data:
                        image_data = image_data.split(',')[1]
                    
                    image_bytes = base64.b64decode(image_data)
                    
                    with tempfile.NamedTemporaryFile(delete=False, suffix='.jpg') as temp_file:
                        temp_file.write(image_bytes)
                        temp_image_path = temp_file.name
                        image_path = temp_image_path
                        print(f"[DEBUG] 임시 파일 생성: {temp_image_path}")
                        
                except Exception as e:
                    return jsonify({"success": False, "error": f"이미지 디코딩 실패: {str(e)}"}), 400
            
            if not os.path.exists(image_path):
                return jsonify({"success": False, "error": f"이미지 파일을 찾을 수 없습니다: {image_path}"}), 400
            
            # CLI 명령 구성
            cmd = [
                PYTHON_PATH, SCRIPT_PATH, "evaluate-image",
                "--image", image_path,
                "--ocr-method", ocr_method,
                "--similarity-threshold", str(similarity_threshold),
                "--nli-batch", str(nli_batch)
            ]
            
            if use_gpu:
                cmd.append("--use-gpu")
            if fp16:
                cmd.append("--fp16")
            
            print(f"실행 명령: {' '.join(cmd)}")
            
            # CLI 실행
            start_time = datetime.now()
            
            env = os.environ.copy()
            env['PYTHONIOENCODING'] = 'utf-8'
            env['PYTHONLEGACYWINDOWSFSENCODING'] = '1'
            
            try:
                result = subprocess.run(
                    cmd,
                    capture_output=True,
                    text=True,
                    timeout=180,  # 3분 타임아웃
                    cwd="C:/Smart_IT",
                    env=env,
                    encoding='utf-8',
                    errors='replace'
                )
            except subprocess.TimeoutExpired:
                return jsonify({
                    "success": False,
                    "error": "이미지 평가 시간 초과 (3분)",
                    "elapsed_seconds": 180
                }), 500
                
            end_time = datetime.now()
            elapsed = (end_time - start_time).total_seconds()
            
            # 결과 파싱
            if result.returncode == 0:
                output = result.stdout
                parsed_result = parse_cli_output(output)
                
                # 평가 결과를 데이터베이스에 저장
                if user_id and parsed_result.get('reliability_score') is not None:
                    try:
                        conn = get_db_connection()
                        conn.execute(
                            'INSERT INTO news_evaluations (user_id, news_url, evaluation_score, evaluation_result) VALUES (?, ?, ?, ?)',
                            (user_id, 'image_evaluation', parsed_result['reliability_score'], json.dumps(parsed_result))
                        )
                        conn.commit()
                        conn.close()
                        print(f"[DEBUG] 이미지 평가 결과 저장 완료 - 사용자 {user_id}")
                    except Exception as e:
                        print(f"[DEBUG] 평가 결과 저장 실패: {e}")
                
                return jsonify({
                    "success": True,
                    "message": "이미지 신뢰도 평가 완료",
                    "timestamp": datetime.now().isoformat(),
                    "elapsed_seconds": round(elapsed, 1),
                    "parameters": {
                        "similarity_threshold": similarity_threshold,
                        "ocr_method": ocr_method,
                        "use_gpu": use_gpu,
                        "fp16": fp16,
                        "nli_batch": nli_batch
                    },
                    "result": parsed_result
                })
            else:
                return jsonify({
                    "success": False,
                    "error": "이미지 평가 실패",
                    "return_code": result.returncode,
                    "stdout": result.stdout,
                    "stderr": result.stderr,
                    "elapsed_seconds": round(elapsed, 1)
                }), 500
                
        finally:
            # 임시 파일 삭제
            if temp_image_path and os.path.exists(temp_image_path):
                try:
                    os.unlink(temp_image_path)
                    print(f"[DEBUG] 임시 파일 삭제: {temp_image_path}")
                except Exception as e:
                    print(f"[DEBUG] 임시 파일 삭제 실패: {e}")
                    
    except Exception as e:
        return jsonify({
            "success": False,
            "error": f"요청 처리 오류: {str(e)}",
            "timestamp": datetime.now().isoformat()
        }), 500

if __name__ == '__main__':
    print("🚀 Veriscope 통합 API 서버를 시작합니다...")
    print("📊 데이터베이스:", DATABASE_PATH)
    print("🤖 Veriscope CLI:", SCRIPT_PATH)
    print("🌐 서버 주소: http://localhost:5000")
    print("")
    print("📋 API 엔드포인트:")
    print("  🔐 사용자 인증:")
    print("    - POST /api/auth/login")
    print("    - POST /api/auth/signup") 
    print("    - POST /api/auth/forgot-password")
    print("    - GET  /api/users (개발용)")
    print("")
    print("  📰 뉴스 신뢰도 평가:")
    print("    - POST /api/evaluate")
    print("    - POST /api/evaluate-image")
    print("")
    print("  ⚡ 시스템:")
    print("    - GET  /api/health")
    print("    - GET  / (API 정보)")
    print("")
    
    app.run(debug=True, host='0.0.0.0', port=5001)