<?php

include_once 'Profile.php';


class Transaction {
	private $timeStamp;
	private $uid;

	public function __construct() {
		$this->timeStamp = time();
		$this->uid = $this->newId();
	}

	private function newId() {
		static $idCounter = 0;
		return $idCounter++;
	}

	public function getUid() {
		return $this->uid;
	}

	public function getTimeStamp() {
		return $this->timeStamp;
	}
}

class Session {    
	public static function get() {
		static $sSession = null;
		if ($sSession === NULL)
			$sSession = new Session();
		return $sSession;
	}

	public function clear() {
		$this->setSignatureToken("");
		$this->setUserRoles(array());
	}

	public function getDatabase($user) {
		$databasePath = $user."/.git";
		if (!file_exists($databasePath))
			return null;
		return new GitDatabase($databasePath);
	}

	public function getProfile($user) {
		$database = $this->getDatabase($user);
		if ($database === null)
			return null;
		return new Profile($database, "profile", "");
	}

	public function getMainUserIdentity($user) {
		$profile = $this->getProfile($user);
		if ($profile === null)
			return null;
		return $profile->getUserIdentityAt(0);
	}

	public function getAccountUser() {
		if (!isset($_SESSION['account_user']))
			return "";
		return $_SESSION['account_user'];
	}
	
	public function setAccountUser($user) {
		$_SESSION['account_user'] = $user;
	}

	public function addTransaction($transaction) {
		if (!isset($_SESSION['transactions']))
			$_SESSION['transactions'] = array();
		$_SESSION['transactions'][$transaction->getUid()] = serialize($transaction);
	}

	public function getTransaction($transactionId) {
		if (!isset($_SESSION['transactions'][$transactionId]))
			return null;
		return unserialize($_SESSION['transactions'][$transactionId]);
	}

	public function removeTransaction($transaction) {
		unset($_SESSION['transactions'][$transaction->getUid()]);
	}

	public function setUserRoles($roles) {
		$_SESSION['user_roles'] = $roles;
	}
	
	public function getUserRoles() {
		if (!isset($_SESSION['user_roles']))
			return array();
		return $_SESSION['user_roles'];
	}
}

?>
