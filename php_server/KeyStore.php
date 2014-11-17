<?PHP

include_once 'UserData.php';

class KeyStore extends UserData {
	public function __construct($database, $branch, $directory) {
		parent::__construct($database, $branch, $directory);
	}

	public function readAsymmetricKey($keyId, &$publicKey) {
		$ok = $this->read($keyId."/publicKey", $publicKey);
		if (!$ok)
			return false;
		return true;
	}

}

?>
