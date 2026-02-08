<?php
// auth/signup.php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");
header("Access-Control-Max-Age: 3600");
header("Access-Control-Allow-Headers: Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");

include_once '../config/database.php';

// POST 요청만 허용
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(array("success" => false, "message" => "Method not allowed"));
    exit();
}

// JSON 데이터 읽기
$data = json_decode(file_get_contents("php://input"));

if (!$data || !isset($data->name) || !isset($data->email) || !isset($data->password)) {
    http_response_code(400);
    echo json_encode(array("success" => false, "message" => "모든 필드를 입력해주세요."));
    exit();
}

// 입력 검증
if (strlen($data->password) < 6) {
    http_response_code(400);
    echo json_encode(array("success" => false, "message" => "비밀번호는 6자 이상이어야 합니다."));
    exit();
}

if (!filter_var($data->email, FILTER_VALIDATE_EMAIL)) {
    http_response_code(400);
    echo json_encode(array("success" => false, "message" => "올바른 이메일 형식을 입력해주세요."));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();

    // 이메일 중복 확인
    $check_query = "SELECT id FROM users WHERE email = :email LIMIT 1";
    $check_stmt = $db->prepare($check_query);
    $check_stmt->bindParam(":email", $data->email);
    $check_stmt->execute();

    if ($check_stmt->fetch(PDO::FETCH_ASSOC)) {
        http_response_code(409);
        echo json_encode(array("success" => false, "message" => "이미 사용중인 이메일입니다."));
        exit();
    }

    // 비밀번호 해시화
    $hashed_password = password_hash($data->password, PASSWORD_DEFAULT);

    // 사용자 생성
    $insert_query = "INSERT INTO users (name, email, password) VALUES (:name, :email, :password)";
    $insert_stmt = $db->prepare($insert_query);
    $insert_stmt->bindParam(":name", $data->name);
    $insert_stmt->bindParam(":email", $data->email);
    $insert_stmt->bindParam(":password", $hashed_password);

    if ($insert_stmt->execute()) {
        $user_id = $db->lastInsertId();
        
        http_response_code(201);
        echo json_encode(array(
            "success" => true,
            "message" => "회원가입이 완료되었습니다.",
            "data" => array(
                "id" => $user_id,
                "name" => $data->name,
                "email" => $data->email
            )
        ));
    } else {
        http_response_code(500);
        echo json_encode(array("success" => false, "message" => "회원가입 중 오류가 발생했습니다."));
    }

} catch(Exception $e) {
    http_response_code(500);
    echo json_encode(array(
        "success" => false,
        "message" => "서버 오류가 발생했습니다."
    ));
    error_log("Signup error: " . $e->getMessage());
}
?>