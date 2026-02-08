import sqlite3
import hashlib
from datetime import datetime

def create_database(reset_data=False):
    """Veriscope 데이터베이스를 생성하고 초기 설정을 수행합니다."""
    
    # 데이터베이스 연결
    conn = sqlite3.connect('database/veriscope.db')
    cursor = conn.cursor()
    
    if reset_data:
        print("🗑️  기존 데이터를 모두 삭제합니다...")
        # 모든 테이블의 데이터 삭제 (테이블 구조는 유지)
        cursor.execute('DELETE FROM user_sessions')
        cursor.execute('DELETE FROM news_evaluations')
        cursor.execute('DELETE FROM users')
        print("✅ 기존 데이터가 삭제되었습니다.")
    
    # 사용자 테이블 생성
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            email TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL,
            email_verified INTEGER DEFAULT 0,
            verification_token TEXT,
            reset_token TEXT,
            reset_token_expires TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    
    # 뉴스 평가 기록 테이블 생성
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS news_evaluations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            news_url TEXT NOT NULL,
            evaluation_score REAL NOT NULL,
            evaluation_result TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    ''')
    
    # 세션 테이블 생성 (선택사항)
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS user_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            session_token TEXT UNIQUE NOT NULL,
            expires_at TIMESTAMP NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    ''')
    
    # 인덱스 생성
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_evaluations_user_id ON news_evaluations(user_id)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_sessions_token ON user_sessions(session_token)')
    
    # 초기 테스트 데이터 삽입
    demo_password = hashlib.sha256('demo123'.encode()).hexdigest()
    cursor.execute('''
        INSERT OR IGNORE INTO users (name, email, password, email_verified) 
        VALUES (?, ?, ?, ?)
    ''', ('데모 사용자', 'demo@example.com', demo_password, 1))
    
    # 변경사항 커밋
    conn.commit()
    
    print("✅ SQLite 데이터베이스가 성공적으로 생성되었습니다!")
    print(f"📁 데이터베이스 파일: database/veriscope.db")
    
    # 테이블 정보 출력
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = cursor.fetchall()
    print(f"📋 생성된 테이블: {[table[0] for table in tables]}")
    
    # 사용자 수 확인
    cursor.execute("SELECT COUNT(*) FROM users")
    user_count = cursor.fetchone()[0]
    print(f"👤 등록된 사용자 수: {user_count}")
    
    conn.close()

def reset_database():
    """데이터베이스의 모든 데이터를 초기화합니다."""
    response = input("⚠️  모든 검사 이력과 사용자 데이터를 삭제하시겠습니까? (y/N): ")
    if response.lower() in ['y', 'yes', '네', 'ㅇ']:
        create_database(reset_data=True)
        print("🔄 데이터베이스가 초기화되었습니다!")
        print("📊 현재 검사 이력: 0개")
    else:
        print("❌ 초기화가 취소되었습니다.")

def show_database_info():
    """현재 데이터베이스 상태를 보여줍니다."""
    try:
        conn = sqlite3.connect('database/veriscope.db')
        cursor = conn.cursor()
        
        # 사용자 수 확인
        cursor.execute("SELECT COUNT(*) FROM users")
        user_count = cursor.fetchone()[0]
        
        # 검사 이력 수 확인
        cursor.execute("SELECT COUNT(*) FROM news_evaluations")
        evaluation_count = cursor.fetchone()[0]
        
        # 세션 수 확인
        cursor.execute("SELECT COUNT(*) FROM user_sessions")
        session_count = cursor.fetchone()[0]
        
        print(f"""
📊 현재 데이터베이스 상태:
   👤 등록된 사용자: {user_count}개
   📝 검사 이력: {evaluation_count}개  
   🔐 활성 세션: {session_count}개
        """)
        
        conn.close()
    except Exception as e:
        print(f"❌ 데이터베이스 조회 오류: {e}")

def show_users():
    """등록된 모든 사용자 계정을 보여줍니다."""
    try:
        conn = sqlite3.connect('database/veriscope.db')
        cursor = conn.cursor()
        
        # 모든 사용자 정보 조회
        cursor.execute("""
            SELECT id, name, email, email_verified, created_at 
            FROM users 
            ORDER BY id
        """)
        users = cursor.fetchall()
        
        if not users:
            print("❌ 등록된 사용자가 없습니다.")
        else:
            print(f"\n👥 등록된 사용자 목록 (총 {len(users)}명):")
            print("=" * 80)
            for user in users:
                user_id, name, email, verified, created_at = user
                status = "✅ 인증됨" if verified else "⏳ 미인증"
                print(f"ID: {user_id} | 이름: {name} | 이메일: {email}")
                print(f"        상태: {status} | 가입일: {created_at}")
                
                # 해당 사용자의 검사 이력 수 확인
                cursor.execute("SELECT COUNT(*) FROM news_evaluations WHERE user_id = ?", (user_id,))
                eval_count = cursor.fetchone()[0]
                print(f"        검사 이력: {eval_count}개")
                print("-" * 80)
        
        conn.close()
    except Exception as e:
        print(f"❌ 사용자 조회 오류: {e}")

def delete_user(user_id):
    """특정 사용자를 ID로 삭제합니다."""
    try:
        conn = sqlite3.connect('database/veriscope.db')
        cursor = conn.cursor()
        
        # 해당 ID의 사용자 정보 확인
        cursor.execute("""
            SELECT id, name, email, email_verified, created_at 
            FROM users 
            WHERE id = ?
        """, (user_id,))
        user = cursor.fetchone()
        
        if not user:
            print(f"❌ ID {user_id}에 해당하는 사용자를 찾을 수 없습니다.")
            conn.close()
            return
        
        user_id, name, email, verified, created_at = user
        status = "✅ 인증됨" if verified else "⏳ 미인증"
        
        # 검사 이력 수 확인
        cursor.execute("SELECT COUNT(*) FROM news_evaluations WHERE user_id = ?", (user_id,))
        eval_count = cursor.fetchone()[0]
        
        # 세션 수 확인
        cursor.execute("SELECT COUNT(*) FROM user_sessions WHERE user_id = ?", (user_id,))
        session_count = cursor.fetchone()[0]
        
        # 삭제 확인 메시지
        print(f"\n⚠️  다음 사용자를 삭제하시겠습니까?")
        print("=" * 80)
        print(f"ID: {user_id} | 이름: {name} | 이메일: {email}")
        print(f"        상태: {status} | 가입일: {created_at}")
        print(f"        검사 이력: {eval_count}개")
        print(f"        활성 세션: {session_count}개")
        print("=" * 80)
        
        response = input("정말로 삭제하시겠습니까? (y/N): ")
        if response.lower() in ['y', 'yes', '네', 'ㅇ']:
            # CASCADE 설정으로 관련 데이터 자동 삭제
            cursor.execute("DELETE FROM users WHERE id = ?", (user_id,))
            conn.commit()
            print(f"✅ 사용자 '{name}' (ID: {user_id})이(가) 삭제되었습니다.")
            print(f"   - 검사 이력 {eval_count}개 삭제됨")
            print(f"   - 세션 {session_count}개 삭제됨")
        else:
            print("❌ 삭제가 취소되었습니다.")
        
        conn.close()
    except Exception as e:
        print(f"❌ 사용자 삭제 오류: {e}")

if __name__ == "__main__":
    import sys
    
    if len(sys.argv) > 1:
        command = sys.argv[1].lower()
        if command in ['reset', 'clear', '초기화']:
            reset_database()
        elif command in ['info', 'status', '상태']:
            show_database_info()
        elif command in ['users', 'list', '사용자', '계정']:
            show_users()
        elif command in ['del', 'delete', 'remove', '삭제']:
            if len(sys.argv) > 2:
                try:
                    user_id = int(sys.argv[2])
                    delete_user(user_id)
                except ValueError:
                    print("❌ 유효한 사용자 ID를 입력해주세요. (숫자)")
            else:
                print("❌ 삭제할 사용자 ID를 지정해주세요.")
                print("   사용법: python create_database.py del <사용자_ID>")
        elif command in ['help', 'h']:
            print("""
🔧 Veriscope 데이터베이스 관리 도구

사용법:
    python create_database.py              # 데이터베이스 생성
    python create_database.py reset        # 모든 데이터 초기화
    python create_database.py info         # 현재 상태 확인
    python create_database.py users        # 등록된 사용자 계정 목록 보기
    python create_database.py del <ID>     # 특정 사용자 삭제 (예: del 19)
    python create_database.py help         # 도움말
            """)
        else:
            print("❌ 알 수 없는 명령어입니다. 'help'를 사용해보세요.")
    else:
        create_database()