<?php
// auth/login.php
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

if (!$data || !isset($data->email) || !isset($data->password)) {
    http_response_code(400);
    echo json_encode(array("success" => false, "message" => "이메일과 비밀번호를 입력해주세요."));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();

    // 사용자 조회
    $query = "SELECT id, name, email, password FROM users WHERE email = :email LIMIT 1";
    $stmt = $db->prepare($query);
    $stmt->bindParam(":email", $data->email);
    $stmt->execute();

    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($user && password_verify($data->password, $user['password'])) {
        // 로그인 성공
        unset($user['password']); // 비밀번호 제거
        
        http_response_code(200);
        echo json_encode(array(
            "success" => true,
            "message" => "로그인 성공",
            "data" => $user
        ));
    } else {
        // 로그인 실패
        http_response_code(401);
        echo json_encode(array(
            "success" => false,
            "message" => "이메일 또는 비밀번호가 잘못되었습니다."
        ));
    }

} catch(Exception $e) {
    http_response_code(500);
    echo json_encode(array(
        "success" => false,
        "message" => "서버 오류가 발생했습니다."
    ));
    error_log("Login error: " . $e->getMessage());
}
?>