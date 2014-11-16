<?php

include_once 'Signature.php';
include_once 'UserData.php';


class Contact extends UserData {
	private $userIdentity;
	
	public function __construct($userIdentity, $directory) {
		parent::__construct($userIdentity->getDatabase(), $userIdentity->getBranch(), $directory);
		
		$this->userIdentity = $userIdentity;
	}

	public function setUid($uid) {
		$this->write("uid", $uid);
	}

	public function getUid() {
		$uid;
		$this->read("uid", $uid);
		return $uid;
	}

	public function verify($keyId, $data, $signature) {
		$publicKey;
		$ok = $this->getKeySet($keyId, $publicKey);
		if (!$ok)
			return false;
		$signatureVerifier = new SignatureVerifier($publicKey);
		return $signatureVerifier->verify($data, $signature);
	}

	public function getKeySet($keyId, &$publicKey) {
		$privateKeyId;
		$ok = $this->read("keys/".$keyId."/keyId", $privateKeyId);
		if ($ok) {
			$profile = $this->userIdentity->getProfile();
			$keyStore = $profile->getUserIdentityKeyStore($this->userIdentity);
			$ok = $keyStore->readAsymmetricKey($keyId, $publicKey);
			if (!$ok)
				return false;
		} else {
			$ok = $this->read("keys/".$keyId."/publicKey", $publicKey);
			if (!$ok)
				return false;
		}
		return true;
	}

	public function addKeySet($keyId, $publicKey) {
		$this->write("keys/".$keyId."/publicKey", $publicKey);
	}

	public function setMainKeyId($mainKeyId) {
		$this->write("mainKeyId", $mainKeyId);
	}

	public function getMainKeyId() {
		$mainKeyId;
		$this->read("mainKeyId", $mainKeyId);
		return $mainKeyId;
	}

	public function addData($path, $data) {
		$this->write($path, $data);
	}

	public function getAddress() {
		$serverUser = "";
		$server = "";
		$this->read("serverUser", $serverUser);
		$this->read("server", $server);
		return $serverUser."@".$server;
	}

	public function setAddress($address) {
		$parts = explode("@", $address);
		if (count($parts) == 2) {
			$this->write("serverUser", $parts[0]);
			$this->write("server", $parts[1]);
		}
	}
}

?>
