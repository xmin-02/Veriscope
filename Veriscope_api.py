# flask_api.py - Smart IT 신뢰도 평가 Flask API (CLI 래퍼)
# --------------------------------------------------------------------------------------------
# CLI 명령을 래핑하는 방식의 Flask API 
# --------------------------------------------------------------------------------------------

from flask import Flask, request, jsonify
from flask_cors import CORS
import subprocess
import json
import os
import re
import base64
import tempfile
import sqlite3
import hashlib
import secrets
import smtplib
import uuid
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from datetime import datetime, timedelta
from werkzeug.utils import secure_filename

app = Flask(__name__)
CORS(app)

# 파이썬 실행 경로
PYTHON_PATH = "C:/Smart_IT/.venv/Scripts/python.exe"
SCRIPT_PATH = "C:/Smart_IT/Veriscope.py"

# 데이터베이스 설정
DATABASE_PATH = 'database/veriscope.db'

# 이메일 설정 (실제 환경에서는 환경변수 사용 권장)
EMAIL_CONFIG = {
    'SMTP_SERVER': 'smtp.gmail.com',
    'SMTP_PORT': 587,
    'EMAIL_ADDRESS': 'smartit.ngms@gmail.com',
    'EMAIL_PASSWORD': 'gxut kmss jrjo obaq',
    'FROM_NAME': 'Veriscope 팀'
}

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

def validate_phone(phone):
    """전화번호 형식을 검증합니다."""
    # 010으로 시작하는 11자리 숫자
    pattern = r'^010\d{8}$'
    return re.match(pattern, phone) is not None

def hash_password(password):
    """비밀번호를 해시화합니다."""
    return hashlib.sha256(password.encode()).hexdigest()

def generate_verification_code():
    """6자리 인증 코드를 생성합니다."""
    return str(uuid.uuid4().int)[:6]

def send_verification_email(email, verification_code):
    """이메일 인증 코드를 발송합니다."""
    try:
        # 이메일 내용 구성
        subject = "[Veriscope] 이메일 인증 코드"
        
        html_body = f"""
        <html>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
            <div style="background: linear-gradient(135deg, #2196F3 0%, #1976D2 100%); padding: 30px; border-radius: 10px; color: white; text-align: center;">
                <h1 style="margin: 0; font-size: 28px;">Veriscope</h1>
                <p style="margin: 10px 0 0 0; opacity: 0.9;">뉴스 신뢰도 평가 서비스</p>
            </div>
            
            <div style="padding: 40px 30px; background: #f8f9fa; border-radius: 0 0 10px 10px;">
                <h2 style="color: #333; margin-bottom: 20px;">이메일 인증</h2>
                
                <p style="color: #666; line-height: 1.6; margin-bottom: 30px;">
                    안녕하세요! Veriscope에 가입해 주셔서 감사합니다.<br>
                    아래 인증 코드를 앱에 입력하여 이메일 인증을 완료해 주세요.
                </p>
                
                <div style="background: white; padding: 25px; border-radius: 8px; text-align: center; border: 2px dashed #2196F3;">
                    <p style="color: #333; margin-bottom: 10px; font-size: 14px;">인증 코드</p>
                    <h1 style="color: #2196F3; font-size: 36px; letter-spacing: 8px; margin: 0; font-family: 'Courier New', monospace;">
                        {verification_code}
                    </h1>
                </div>
                
                <p style="color: #999; font-size: 12px; margin-top: 30px; line-height: 1.5;">
                    • 이 인증 코드는 10분간 유효합니다.<br>
                    • 본인이 요청하지 않은 경우 이 이메일을 무시하세요.<br>
                    • 문의사항은 support@veriscope.com으로 연락해 주세요.
                </p>
            </div>
        </body>
        </html>
        """
        
        # 이메일 메시지 생성
        msg = MIMEMultipart('alternative')
        msg['From'] = f"{EMAIL_CONFIG['FROM_NAME']} <{EMAIL_CONFIG['EMAIL_ADDRESS']}>"
        msg['To'] = email
        msg['Subject'] = subject
        
        # HTML 내용 추가
        html_part = MIMEText(html_body, 'html', 'utf-8')
        msg.attach(html_part)
        
        # SMTP 서버 연결 및 이메일 발송
        with smtplib.SMTP(EMAIL_CONFIG['SMTP_SERVER'], EMAIL_CONFIG['SMTP_PORT']) as server:
            server.starttls()
            server.login(EMAIL_CONFIG['EMAIL_ADDRESS'], EMAIL_CONFIG['EMAIL_PASSWORD'])
            server.send_message(msg)
        
        return True
    except Exception as e:
        print(f"[ERROR] 이메일 발송 실패: {str(e)}")
        return False

def send_password_reset_verification_email(email, verification_code):
    """비밀번호 찾기 인증 코드를 발송합니다."""
    try:
        # 이메일 내용 구성
        subject = "[Veriscope] 비밀번호 재설정 인증 코드"
        
        html_body = f"""
        <html>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
            <div style="background: linear-gradient(135deg, #ff6b6b 0%, #ee5a24 100%); padding: 30px; border-radius: 10px; color: white; text-align: center;">
                <h1 style="margin: 0; font-size: 28px;">Veriscope</h1>
                <p style="margin: 10px 0 0 0; opacity: 0.9;">비밀번호 재설정</p>
            </div>
            
            <div style="padding: 40px 30px; background: #f8f9fa; border-radius: 0 0 10px 10px;">
                <h2 style="color: #333; margin-bottom: 20px;">🔐 비밀번호 재설정</h2>
                
                <p style="color: #666; line-height: 1.6; margin-bottom: 30px;">
                    비밀번호 재설정을 요청하셨습니다.<br>
                    아래 인증 코드를 앱에 입력하여 본인 확인을 완료해 주세요.
                </p>
                
                <div style="background: white; padding: 25px; border-radius: 8px; text-align: center; border: 2px dashed #ff6b6b;">
                    <p style="color: #333; margin-bottom: 10px; font-size: 14px;">인증 코드</p>
                    <h1 style="color: #ff6b6b; font-size: 36px; letter-spacing: 8px; margin: 0; font-family: 'Courier New', monospace;">
                        {verification_code}
                    </h1>
                </div>
                
                <p style="color: #999; font-size: 12px; margin-top: 30px; line-height: 1.5;">
                    • 이 인증 코드는 5분간 유효합니다.<br>
                    • 본인이 요청하지 않은 경우 이 이메일을 무시하세요.<br>
                    • 인증 완료 후 새로운 비밀번호를 설정할 수 있습니다.<br>
                    • 문의사항은 support@veriscope.com으로 연락해 주세요.
                </p>
            </div>
        </body>
        </html>
        """
        
        # 이메일 메시지 생성
        msg = MIMEMultipart('alternative')
        msg['From'] = f"{EMAIL_CONFIG['FROM_NAME']} <{EMAIL_CONFIG['EMAIL_ADDRESS']}>"
        msg['To'] = email
        msg['Subject'] = subject
        
        # HTML 내용 추가
        html_part = MIMEText(html_body, 'html', 'utf-8')
        msg.attach(html_part)
        
        # SMTP 서버 연결 및 이메일 발송
        with smtplib.SMTP(EMAIL_CONFIG['SMTP_SERVER'], EMAIL_CONFIG['SMTP_PORT']) as server:
            server.starttls()
            server.login(EMAIL_CONFIG['EMAIL_ADDRESS'], EMAIL_CONFIG['EMAIL_PASSWORD'])
            server.send_message(msg)
        
        return True
    except Exception as e:
        print(f"[ERROR] 비밀번호 찾기 이메일 발송 실패: {str(e)}")
        return False

# =============================================================================
# CLI 파싱 함수
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
        
        # 신뢰도 점수 추출 (정수 및 소수점 모두 지원)
        if "신뢰도:" in line:
            print(f"[DEBUG] 신뢰도 라인 발견: {line}")
            # 소수점도 허용하는 정규식
            match = re.search(r'신뢰도:\s*([\d.]+)%?\s*-?\s*(.+)?', line)
            if match:
                try:
                    score_str = match.group(1)
                    reliability_score = float(score_str)
                    # 0-1 범위면 100을 곱해서 퍼센트로 변환
                    if reliability_score <= 1.0:
                        reliability_score = reliability_score * 100
                    reliability_score = round(reliability_score)  # 정수로 반올림
                    reliability_level = match.group(2).strip() if match.group(2) else "평가됨"
                    print(f"[DEBUG] 파싱 성공: {reliability_score}% - {reliability_level}")
                except ValueError as e:
                    print(f"[DEBUG] 신뢰도 파싱 오류: {e}")
                    reliability_score = 0
                    reliability_level = "파싱 실패"
        
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
    
    print(f"[DEBUG] 최종 파싱 결과:")
    print(f"[DEBUG] - 신뢰도: {reliability_score}% - {reliability_level}")
    print(f"[DEBUG] - 근거 수: {len(evidence_list)}")
    print(f"[DEBUG] - 권장사항: {recommendation}")
    
    # 파싱 성공 여부 결정
    success = reliability_score is not None
    
    return {
        "success": success,
        "reliability_score": reliability_score,
        "reliability_level": reliability_level,
        "recommendation": recommendation,
        "evidence_count": len(evidence_list),
        "evidence": evidence_list
    }

@app.route('/', methods=['GET'])
def home():
    """API 상태 확인"""
    return jsonify({
        "status": "ok",
        "service": "Veriscope - 뉴스 신뢰도 평가 & 사용자 관리 API",
        "version": "2.0",
        "timestamp": datetime.now().isoformat(),
        "description": "통합 API - 사용자 인증 + 뉴스 신뢰도 평가",
        "endpoints": {
            "auth": {
                "login": "POST /auth/login",
                "signup": "POST /auth/signup", 
                "forgot-password": "POST /auth/forgot-password",
                "users": "GET /users"
            },
            "evaluation": {
                "evaluate-url": "POST /evaluate",
                "evaluate-image": "POST /evaluate-image"
            },
            "system": {
                "health": "GET /health"
            }
        }
    })

@app.route('/health', methods=['GET'])
def health():
    """헬스체크"""
    try:
        # 데이터베이스 연결 테스트
        conn = get_db_connection()
        cursor = conn.execute("SELECT COUNT(*) FROM users")
        user_count = cursor.fetchone()[0]
        conn.close()
        
        # CLI 명령 테스트
        result = subprocess.run([
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
                "status": "available" if result.returncode == 0 else "unavailable",
                "python_path": PYTHON_PATH,
                "script_path": SCRIPT_PATH
            }
        })
    except Exception as e:
        return jsonify({
            "status": "unhealthy", 
            "timestamp": datetime.now().isoformat(),
            "error": str(e)
        }), 500

# =============================================================================
# 마이페이지 API
# =============================================================================

@app.route('/user/history', methods=['GET'])
def get_user_history():
    """사용자 검사 내역 조회"""
    try:
        # 임시로 더미 데이터 반환 (실제 구현에서는 JWT 토큰에서 사용자 ID 추출)
        user_id = request.args.get('user_id', '1')  # 임시 사용자 ID
        
        conn = get_db_connection()
        
        # 최근 30개의 검사 내역 조회
        cursor = conn.execute("""
            SELECT id, news_url, evaluation_score, evaluation_result, created_at, user_id
            FROM news_evaluations 
            WHERE user_id = ? 
            ORDER BY created_at DESC 
            LIMIT 30
        """, (user_id,))
        
        rows = cursor.fetchall()
        
        history = []
        for row in rows:
            # evaluation_result JSON 파싱
            try:
                result_data = json.loads(row['evaluation_result']) if row['evaluation_result'] else {}
            except:
                result_data = {}
            
            # URL에서 제목 추출 또는 기본 제목 사용
            if row['news_url'] == 'image_evaluation':
                title = "이미지 검사"
                url = None
                check_type = "IMAGE"
            else:
                # URL에서 간단한 제목 추출
                url_parts = row['news_url'].split('/')
                title = url_parts[-1] if url_parts else "URL 검사"
                if len(title) > 50:
                    title = title[:47] + "..."
                url = row['news_url']
                check_type = "URL"
            
            # 신뢰도 점수
            score = row['evaluation_score'] or 50.0
            is_reliable = score >= 70
            
            # 날짜 포맷팅
            try:
                created_at = datetime.fromisoformat(row['created_at'].replace('Z', '+00:00'))
                formatted_date = created_at.strftime('%Y.%m.%d %H:%M')
            except:
                formatted_date = row['created_at']
            
            history.append({
                "id": row['id'],
                "title": title,
                "url": url,
                "reliabilityScore": float(score),
                "isReliable": is_reliable,
                "checkedAt": formatted_date,
                "type": check_type
            })
        
        conn.close()
        
        return jsonify({
            "success": True,
            "history": history,
            "total": len(history)
        })
        
    except Exception as e:
        print(f"[ERROR] 검사 내역 조회 실패: {e}")
        # 오류 발생 시 더미 데이터 반환
        dummy_history = [
            {
                "id": 1,
                "title": "코로나19 백신 관련 뉴스",
                "url": "https://news.example.com/covid19-vaccine",
                "reliabilityScore": 85.5,
                "isReliable": True,
                "checkedAt": "2024.11.14 15:30",
                "type": "URL"
            },
            {
                "id": 2,
                "title": "경제 정책 발표 관련",
                "url": None,
                "reliabilityScore": 42.3,
                "isReliable": False,
                "checkedAt": "2024.11.13 09:15",
                "type": "IMAGE"
            },
            {
                "id": 3,
                "title": "스포츠 경기 결과",
                "url": "https://sports.example.com/result",
                "reliabilityScore": 91.2,
                "isReliable": True,
                "checkedAt": "2024.11.12 20:45",
                "type": "URL"
            }
        ]
        
        return jsonify({
            "success": True,
            "history": dummy_history,
            "total": len(dummy_history)
        })

@app.route('/user/profile', methods=['GET'])
def get_user_profile():
    """사용자 프로필 정보 조회"""
    try:
        # 임시로 더미 데이터 반환 (실제 구현에서는 JWT 토큰에서 사용자 ID 추출)
        user_id = request.args.get('user_id', '1')
        
        conn = get_db_connection()
        
        # 사용자 정보 조회
        cursor = conn.execute(
            'SELECT id, name, email, created_at FROM users WHERE id = ?',
            (user_id,)
        )
        user = cursor.fetchone()
        
        if user:
            # 검사 통계 조회
            cursor = conn.execute(
                'SELECT COUNT(*) as total_checks FROM news_evaluations WHERE user_id = ?',
                (user_id,)
            )
            total_checks = cursor.fetchone()['total_checks'] or 0
            
            # 신뢰할 수 있는 뉴스 수
            cursor = conn.execute(
                'SELECT COUNT(*) as reliable_count FROM news_evaluations WHERE user_id = ? AND evaluation_score >= 70',
                (user_id,)
            )
            reliable_count = cursor.fetchone()['reliable_count'] or 0
            
            # 가입일 포맷팅
            try:
                join_date = datetime.fromisoformat(user['created_at'].replace('Z', '+00:00'))
                formatted_join_date = join_date.strftime('%Y.%m.%d')
            except:
                formatted_join_date = user['created_at']
            
            conn.close()
            
            return jsonify({
                "success": True,
                "profile": {
                    "id": user['id'],
                    "name": user['name'],
                    "email": user['email'],
                    "joinDate": formatted_join_date,
                    "totalChecks": total_checks,
                    "reliableCount": reliable_count,
                    "unreliableCount": total_checks - reliable_count
                }
            })
        else:
            conn.close()
            # 사용자가 없는 경우 더미 데이터 반환
            return jsonify({
                "success": True,
                "profile": {
                    "id": 1,
                    "name": "사용자",
                    "email": "user@veriscope.com",
                    "joinDate": "2024.01.15",
                    "totalChecks": 3,
                    "reliableCount": 2,
                    "unreliableCount": 1
                }
            })
            
    except Exception as e:
        print(f"[ERROR] 프로필 조회 실패: {e}")
        # 오류 발생 시 더미 데이터 반환
        return jsonify({
            "success": True,
            "profile": {
                "id": 1,
                "name": "사용자",
                "email": "user@veriscope.com",
                "joinDate": "2024.01.15",
                "totalChecks": 3,
                "reliableCount": 2,
                "unreliableCount": 1
            }
        })

# =============================================================================
# 사용자 인증 API
# =============================================================================

@app.route('/auth/login', methods=['POST'])
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
            'SELECT id, name, email, password, email_verified FROM users WHERE email = ?',
            (email,)
        ).fetchone()
        
        if user and user['password'] == hash_password(password):
            if not user['email_verified']:
                conn.close()
                return jsonify({
                    'success': False,
                    'message': '이메일 인증이 필요합니다. 인증 후 다시 로그인해주세요.',
                    'data': {
                        'email_verified': False,
                        'verification_required': True
                    }
                }), 403
            
            conn.close()
            return jsonify({
                'success': True,
                'message': '로그인 성공',
                'data': {
                    'id': user['id'],
                    'name': user['name'],
                    'email': user['email'],
                    'email_verified': True
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

@app.route('/auth/signup', methods=['POST'])
def signup():
    """사용자 회원가입 API"""
    try:
        data = request.get_json()
        
        if not data or 'name' not in data or 'email' not in data or 'phone' not in data or 'password' not in data:
            return jsonify({
                'success': False,
                'message': '모든 필드를 입력해주세요.'
            }), 400
        
        name = data['name'].strip()
        email = data['email'].strip()
        phone = data['phone'].strip()
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
        
        # 전화번호 형식 검증
        phone = phone.replace('-', '')  # 하이픈 제거
        if not validate_phone(phone):
            return jsonify({
                'success': False,
                'message': '올바른 전화번호 형식을 입력해주세요.'
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
        
        # 인증 코드 생성
        verification_code = generate_verification_code()
        
        # 사용자 생성 (이메일 미인증 상태)
        cursor = conn.execute(
            'INSERT INTO users (name, email, phone, password, email_verified, verification_token) VALUES (?, ?, ?, ?, ?, ?)',
            (name, email, phone, hash_password(password), False, verification_code)
        )
        
        user_id = cursor.lastrowid
        conn.commit()
        conn.close()
        
        # 인증 이메일 발송
        email_sent = send_verification_email(email, verification_code)
        
        if email_sent:
            return jsonify({
                'success': True,
                'message': '회원가입이 완료되었습니다. 이메일로 발송된 인증 코드를 입력해주세요.',
                'data': {
                    'id': user_id,
                    'name': name,
                    'email': email,
                    'email_verified': False,
                    'verification_required': True
                }
            }), 201
        else:
            # 이메일 발송 실패 시 사용자 삭제
            conn = get_db_connection()
            conn.execute('DELETE FROM users WHERE id = ?', (user_id,))
            conn.commit()
            conn.close()
            
            return jsonify({
                'success': False,
                'message': '이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.'
            }), 500
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'서버 오류: {str(e)}'
        }), 500

@app.route('/auth/forgot-password', methods=['POST'])
def forgot_password():
    """비밀번호 찾기 - 이메일 인증번호 전송 API"""
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
        
        if not user:
            return jsonify({
                'success': False,
                'message': '등록되지 않은 이메일입니다.'
            }), 404
        
        # 인증번호 생성 및 이메일 전송
        verification_code = generate_verification_code()
        
        # 인증번호를 임시로 저장 (5분 유효)
        expires_at = datetime.now() + timedelta(minutes=5)
        conn.execute(
            'UPDATE users SET reset_verification_code = ?, reset_verification_expires = ? WHERE email = ?',
            (verification_code, expires_at.isoformat(), email)
        )
        conn.commit()
        conn.close()
        
        # 인증번호 이메일 전송
        if send_password_reset_verification_email(email, verification_code):
            return jsonify({
                'success': True,
                'message': '비밀번호 재설정을 위한 인증번호를 이메일로 전송했습니다.'
            }), 200
        else:
            return jsonify({
                'success': False,
                'message': '이메일 전송에 실패했습니다. 다시 시도해주세요.'
            }), 500
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'서버 오류: {str(e)}'
        }), 500

@app.route('/auth/verify-reset-code', methods=['POST'])
def verify_reset_code():
    """비밀번호 찾기 - 인증번호 확인 API"""
    try:
        data = request.get_json()
        
        if not data or 'email' not in data or 'verification_code' not in data:
            return jsonify({
                'success': False,
                'message': '이메일과 인증번호를 입력해주세요.'
            }), 400
        
        email = data['email'].strip()
        verification_code = data['verification_code'].strip()
        
        conn = get_db_connection()
        user = conn.execute(
            'SELECT id, reset_verification_code, reset_verification_expires FROM users WHERE email = ?',
            (email,)
        ).fetchone()
        
        if not user:
            conn.close()
            return jsonify({
                'success': False,
                'message': '등록되지 않은 이메일입니다.'
            }), 404
        
        if not user['reset_verification_code']:
            conn.close()
            return jsonify({
                'success': False,
                'message': '인증번호 요청을 먼저 해주세요.'
            }), 400
        
        # 인증번호 만료 확인
        expires_at = datetime.fromisoformat(user['reset_verification_expires'])
        if datetime.now() > expires_at:
            conn.close()
            return jsonify({
                'success': False,
                'message': '인증번호가 만료되었습니다. 다시 요청해주세요.'
            }), 400
        
        # 인증번호 확인
        if user['reset_verification_code'] != verification_code:
            conn.close()
            return jsonify({
                'success': False,
                'message': '인증번호가 일치하지 않습니다.'
            }), 400
        
        # 인증 성공 - 비밀번호 재설정 토큰 생성
        reset_token = secrets.token_urlsafe(32)
        reset_token_expires = datetime.now() + timedelta(minutes=30)  # 30분 유효
        
        conn.execute(
            'UPDATE users SET reset_token = ?, reset_token_expires = ?, reset_verification_code = NULL, reset_verification_expires = NULL WHERE email = ?',
            (reset_token, reset_token_expires.isoformat(), email)
        )
        conn.commit()
        conn.close()
        
        return jsonify({
            'success': True,
            'message': '인증이 완료되었습니다.',
            'reset_token': reset_token
        }), 200
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'서버 오류: {str(e)}'
        }), 500

@app.route('/auth/verify-email', methods=['POST'])
def verify_email():
    """이메일 인증 코드 검증 API"""
    try:
        data = request.get_json()
        
        if not data or 'email' not in data or 'verification_code' not in data:
            return jsonify({
                'success': False,
                'message': '이메일과 인증 코드를 입력해주세요.'
            }), 400
        
        email = data['email'].strip()
        verification_code = data['verification_code'].strip()
        
        conn = get_db_connection()
        user = conn.execute(
            'SELECT id, name, email_verified, verification_token FROM users WHERE email = ?',
            (email,)
        ).fetchone()
        
        if not user:
            conn.close()
            return jsonify({
                'success': False,
                'message': '존재하지 않는 사용자입니다.'
            }), 404
        
        if user['email_verified']:
            conn.close()
            return jsonify({
                'success': False,
                'message': '이미 인증된 이메일입니다.'
            }), 400
        
        if user['verification_token'] != verification_code:
            conn.close()
            return jsonify({
                'success': False,
                'message': '잘못된 인증 코드입니다.'
            }), 400
        
        # 이메일 인증 완료
        conn.execute(
            'UPDATE users SET email_verified = ?, verification_token = NULL WHERE email = ?',
            (True, email)
        )
        conn.commit()
        conn.close()
        
        return jsonify({
            'success': True,
            'message': '이메일 인증이 완료되었습니다!',
            'data': {
                'id': user['id'],
                'name': user['name'],
                'email': email,
                'email_verified': True
            }
        }), 200
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'서버 오류: {str(e)}'
        }), 500

@app.route('/auth/resend-verification', methods=['POST'])
def resend_verification():
    """인증 코드 재발송 API"""
    try:
        data = request.get_json()
        
        if not data or 'email' not in data:
            return jsonify({
                'success': False,
                'message': '이메일을 입력해주세요.'
            }), 400
        
        email = data['email'].strip()
        
        conn = get_db_connection()
        user = conn.execute(
            'SELECT id, name, email_verified FROM users WHERE email = ?',
            (email,)
        ).fetchone()
        
        if not user:
            conn.close()
            return jsonify({
                'success': False,
                'message': '존재하지 않는 사용자입니다.'
            }), 404
        
        if user['email_verified']:
            conn.close()
            return jsonify({
                'success': False,
                'message': '이미 인증된 이메일입니다.'
            }), 400
        
        # 새로운 인증 코드 생성
        verification_code = generate_verification_code()
        
        # 데이터베이스 업데이트
        conn.execute(
            'UPDATE users SET verification_token = ? WHERE email = ?',
            (verification_code, email)
        )
        conn.commit()
        conn.close()
        
        # 인증 이메일 발송
        email_sent = send_verification_email(email, verification_code)
        
        if email_sent:
            return jsonify({
                'success': True,
                'message': '인증 코드를 재발송했습니다.'
            }), 200
        else:
            return jsonify({
                'success': False,
                'message': '이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.'
            }), 500
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'서버 오류: {str(e)}'
        }), 500

@app.route('/auth/find-email', methods=['POST'])
def find_email():
    """이메일 찾기 API"""
    try:
        data = request.get_json()
        print(f"📧 이메일 찾기 요청: {data}")
        
        if not data or 'name' not in data or 'phone' not in data:
            return jsonify({
                'success': False,
                'message': '이름과 전화번호를 입력해주세요.'
            }), 400
        
        name = data['name'].strip()
        phone = data['phone'].strip()
        
        print(f"📧 검색 정보 - 이름: '{name}', 전화번호: '{phone}'")
        
        if not name or not phone:
            return jsonify({
                'success': False,
                'message': '이름과 전화번호를 입력해주세요.'
            }), 400
        
        conn = get_db_connection()
        
        # 전화번호 정규화하여 검색 (하이픈 있는 것과 없는 것 모두 검색)
        phone_normalized = phone.replace('-', '')
        phone_with_hyphen = f"{phone_normalized[:3]}-{phone_normalized[3:7]}-{phone_normalized[7:]}" if len(phone_normalized) == 11 else phone
        
        user = conn.execute(
            'SELECT email FROM users WHERE name = ? AND (phone = ? OR phone = ?)',
            (name, phone, phone_with_hyphen)
        ).fetchone()
        conn.close()
        
        print(f"📧 검색 쿼리: name='{name}', phone='{phone}' OR phone='{phone_with_hyphen}'")
        
        if user:
            # 이메일 일부 마스킹 (보안을 위해)
            email = user['email']
            masked_email = mask_email(email)
            
            print(f"✅ 이메일 찾기 성공: {masked_email}")
            
            return jsonify({
                'success': True,
                'message': '이메일을 찾았습니다.',
                'data': {
                    'email': masked_email,
                    'full_email': email  # 실제로는 마스킹된 이메일만 보내는 것이 보안상 좋음
                }
            }), 200
        else:
            print("❌ 일치하는 사용자를 찾을 수 없음")
            return jsonify({
                'success': False,
                'message': '입력한 정보와 일치하는 계정을 찾을 수 없습니다.'
            }), 404
    
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'서버 오류: {str(e)}'
        }), 500

@app.route('/auth/reset-password', methods=['POST'])
def reset_password():
    """비밀번호 재설정 API"""
    try:
        data = request.get_json()
        
        if not data or 'email' not in data or 'reset_token' not in data or 'new_password' not in data:
            return jsonify({
                'success': False,
                'message': '이메일, 재설정 토큰, 새 비밀번호를 입력해주세요.'
            }), 400
        
        email = data['email'].strip()
        reset_token = data['reset_token'].strip()
        new_password = data['new_password'].strip()
        
        if len(new_password) < 6:
            return jsonify({
                'success': False,
                'message': '비밀번호는 6자 이상이어야 합니다.'
            }), 400
        
        conn = get_db_connection()
        user = conn.execute(
            'SELECT id, reset_token, reset_token_expires FROM users WHERE email = ?',
            (email,)
        ).fetchone()
        
        if not user:
            conn.close()
            return jsonify({
                'success': False,
                'message': '등록되지 않은 이메일입니다.'
            }), 404
        
        if not user['reset_token'] or user['reset_token'] != reset_token:
            conn.close()
            return jsonify({
                'success': False,
                'message': '유효하지 않은 재설정 토큰입니다.'
            }), 400
        
        # 토큰 만료 확인
        expires_at = datetime.fromisoformat(user['reset_token_expires'])
        if datetime.now() > expires_at:
            conn.close()
            return jsonify({
                'success': False,
                'message': '재설정 토큰이 만료되었습니다. 다시 요청해주세요.'
            }), 400
        
        # 비밀번호 업데이트
        hashed_password = hash_password(new_password)
        conn.execute(
            'UPDATE users SET password = ?, reset_token = NULL, reset_token_expires = NULL WHERE email = ?',
            (hashed_password, email)
        )
        conn.commit()
        conn.close()
        
        return jsonify({
            'success': True,
            'message': '비밀번호가 성공적으로 변경되었습니다.'
        }), 200
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'서버 오류: {str(e)}'
        }), 500

def mask_email(email):
    """이메일 주소 마스킹"""
    if '@' not in email:
        return email

@app.route('/users', methods=['GET'])
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

@app.route('/evaluate', methods=['POST'])
def evaluate():
    """뉴스 신뢰도 평가"""
    print("[DEBUG] /evaluate endpoint called")
    print(f"[DEBUG] Request headers: {dict(request.headers)}")
    try:
        # 요청 데이터 확인
        if not request.is_json:
            print("[DEBUG] Request is not JSON")
            return jsonify({"success": False, "message": "JSON 데이터가 필요합니다.", "data": None}), 200
            
        data = request.get_json()
        print(f"[DEBUG] Received data: {data}")
        url = data.get('url')
        user_id = data.get('user_id')  # 사용자 ID (선택사항)
        
        if not url:
            print("[DEBUG] No URL provided")
            return jsonify({"success": False, "message": "URL이 필요합니다.", "data": None}), 200
            
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
        print("⏱️ 예상 소요시간: 15-30초")
        
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
                encoding='utf-8',  # 명시적 인코딩 설정
                errors='replace'   # 디코딩 오류 시 대체 문자 사용
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
            # 성공적인 실행
            output = result.stdout
            print(f"[DEBUG] CLI Output Length: {len(output)}")
            
            # 새로운 파싱 함수 사용
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
                "data": parsed_result
            })
        else:
            # 실행 실패
            return jsonify({
                "success": False,
                "message": "CLI 실행 실패",
                "data": None,
                "elapsed_seconds": round(elapsed, 1),
                "debug_info": {
                    "return_code": result.returncode,
                    "stdout": result.stdout,
                    "stderr": result.stderr
                }
            }), 200
            
    except subprocess.TimeoutExpired:
        return jsonify({
            "success": False,
            "message": "평가 시간 초과 (2분)",
            "data": None,
            "elapsed_seconds": 120
        }), 200
    except Exception as e:
        return jsonify({
            "success": False,
            "error": f"요청 처리 오류: {str(e)}",
            "timestamp": datetime.now().isoformat()
        }), 500

@app.route('/evaluate-image', methods=['POST'])
def evaluate_image():
    """이미지 신뢰도 평가"""
    print("[DEBUG] /evaluate-image endpoint called")
    print(f"[DEBUG] 요청 시간: {datetime.now()}")
    print(f"[DEBUG] 요청 IP: {request.remote_addr}")
    try:
        # 요청 데이터 확인
        if not request.is_json:
            return jsonify({"success": False, "message": "JSON 데이터가 필요합니다.", "data": None}), 200
            
        data = request.get_json()
        
        # Base64 인코딩된 이미지 데이터 또는 이미지 파일 경로
        image_data = data.get('image_data')  # Base64 인코딩된 이미지
        image_path = data.get('image_path')  # 이미지 파일 경로
        user_id = data.get('user_id')  # 사용자 ID (선택사항)
        
        if not image_data and not image_path:
            return jsonify({"success": False, "message": "image_data 또는 image_path가 필요합니다.", "data": None}), 200
        
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
                # Base64 디코딩
                try:
                    # data:image/jpeg;base64, 등의 prefix 제거
                    if ',' in image_data:
                        image_data = image_data.split(',')[1]
                    
                    image_bytes = base64.b64decode(image_data)
                    
                    # 임시 파일 생성 (delete=False로 수동 관리)
                    with tempfile.NamedTemporaryFile(delete=False, suffix='.jpg') as temp_file:
                        temp_file.write(image_bytes)
                        temp_image_path = temp_file.name
                        image_path = temp_image_path
                        print(f"[DEBUG] 임시 파일 생성: {temp_image_path}")
                        
                except Exception as e:
                    return jsonify({"error": f"이미지 디코딩 실패: {str(e)}"}), 400
            
            # 이미지 파일 존재 확인
            if not os.path.exists(image_path):
                return jsonify({"error": f"이미지 파일을 찾을 수 없습니다: {image_path}"}), 400
            
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
            
            # 환경변수 설정으로 유니코드 문제 해결
            env = os.environ.copy()
            env['PYTHONIOENCODING'] = 'utf-8'
            env['PYTHONLEGACYWINDOWSFSENCODING'] = '1'
            
            try:
                result = subprocess.run(
                    cmd,
                    capture_output=True,
                    text=True,
                    timeout=180,  # 3분 타임아웃 (이미지 처리는 더 오래 걸릴 수 있음)
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
                # 성공적인 실행
                output = result.stdout
                print(f"[DEBUG] CLI Output Length: {len(output)}")
                
                # CLI 출력에서 JSON 결과 추출 시도
                parsed_result = None
                try:
                    # JSON_RESULT: 태그가 있는지 확인
                    lines = output.split('\n')
                    for line in lines:
                        line = line.strip()
                        if line.startswith('JSON_RESULT:'):
                            json_str = line[12:]  # "JSON_RESULT:" 제거
                            parsed_result = json.loads(json_str)
                            print(f"[DEBUG] JSON 파싱 성공: {json_str[:200]}...")
                            break
                        elif line.startswith('{') and line.endswith('}'):
                            parsed_result = json.loads(line)
                            print(f"[DEBUG] 일반 JSON 파싱 성공")
                            break
                except Exception as e:
                    print(f"[DEBUG] JSON 파싱 실패: {e}")
                    pass
                
                # JSON 파싱 실패 시 기존 파싱 방식 사용
                if not parsed_result:
                    print(f"[DEBUG] 기존 파싱 방식 사용")
                    print(f"[DEBUG] CLI 전체 출력:\n{output}")
                    parsed_result = parse_cli_output(output)
                
                # JSON에서 파싱된 결과를 API 표준 형식으로 변환
                if parsed_result and parsed_result.get("success"):
                    api_result = {
                        "reliability_score": parsed_result.get("reliability_score"),
                        "reliability_level": parsed_result.get("reliability_level"), 
                        "recommendation": parsed_result.get("recommendation"),
                        "evidence": parsed_result.get("evidence", [])
                    }
                    
                    print(f"[DEBUG] 성공 응답 반환 - 신뢰도: {api_result.get('reliability_score')}%")
                    print(f"[DEBUG] 근거 자료 수: {len(api_result.get('evidence', []))}")
                    print(f"[DEBUG] 근거 자료 상세: {json.dumps(api_result.get('evidence', []), indent=2, ensure_ascii=False)}")
                    
                    # 이미지 평가 결과를 데이터베이스에 저장
                    if user_id and api_result.get('reliability_score') is not None:
                        try:
                            conn = get_db_connection()
                            conn.execute(
                                'INSERT INTO news_evaluations (user_id, news_url, evaluation_score, evaluation_result) VALUES (?, ?, ?, ?)',
                                (user_id, 'image_evaluation', api_result['reliability_score'], json.dumps(api_result))
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
                        "data": api_result
                    })
                    
                else:
                    # CLI에서 실패한 경우
                    error_msg = parsed_result.get("error", "알 수 없는 오류") if parsed_result else "결과 파싱 실패"
                    return jsonify({
                        "success": False,
                        "error": f"이미지 평가 실패: {error_msg}",
                        "elapsed_seconds": round(elapsed, 1),
                        "raw_output": output[:1000] if len(output) > 1000 else output
                    }), 500
                

            else:
                # 실행 실패
                return jsonify({
                    "success": False,
                    "error": "이미지 평가 실패",
                    "return_code": result.returncode,
                    "stdout": result.stdout,
                    "stderr": result.stderr,
                    "elapsed_seconds": round(elapsed, 1)
                }), 500
        
        except Exception as cli_error:
            return jsonify({
                "success": False,
                "error": f"CLI 실행 중 오류: {str(cli_error)}",
                "elapsed_seconds": round((datetime.now() - start_time).total_seconds(), 1) if 'start_time' in locals() else 0
            }), 500
                
        finally:
            # 임시 파일 삭제 (CLI 실행 후)
            if temp_image_path and os.path.exists(temp_image_path):
                try:
                    os.unlink(temp_image_path)
                    print(f"[DEBUG] 임시 파일 삭제: {temp_image_path}")
                except Exception as e:
                    print(f"[DEBUG] 임시 파일 삭제 실패: {e}")
                    pass
                    
    except subprocess.TimeoutExpired:
        return jsonify({
            "success": False,
            "message": "이미지 평가 시간 초과 (3분)",
            "data": None,
            "elapsed_seconds": 180
        }), 200
    except Exception as e:
        return jsonify({
            "success": False,
            "message": f"요청 처리 오류: {str(e)}",
            "data": None,
            "elapsed_seconds": 0
        }), 200

if __name__ == '__main__':
    print("🚀 Veriscope 통합 API 서버 시작...")
    print("� 데이터베이스:", DATABASE_PATH)
    print("🤖 Veriscope CLI:", SCRIPT_PATH)
    print("🌐 서버 주소: http://localhost:5004")
    print()
    print("📋 API 엔드포인트:")
    print("  🔐 사용자 인증:")
    print("    - POST /auth/login")
    print("    - POST /auth/signup") 
    print("    - POST /auth/forgot-password")
    print("    - GET  /users (개발용)")
    print()
    print("  📰 뉴스 신뢰도 평가:")
    print("    - POST /evaluate")
    print("    - POST /evaluate-image")
    print()
    print("  ⚡ 시스템:")
    print("    - GET  /health")
    print("    - GET  / (API 정보)")
    print()
    print("🎯 데모 계정: demo@example.com / demo123")
    print()
    
    # Flask 앱 실행 (외부 접근 허용)
    import logging
    logging.basicConfig(level=logging.DEBUG)
    app.logger.setLevel(logging.DEBUG)
    app.run(host='0.0.0.0', port=5004, debug=False, threaded=True)