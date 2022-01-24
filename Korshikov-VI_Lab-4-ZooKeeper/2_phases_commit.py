from logging import basicConfig
from threading import Thread
from time import sleep
from random import random
from kazoo.client import KazooClient

basicConfig()


class Client(Thread):
    def __init__(self, root: str, idx: int):
        super().__init__()
        self.url = f"{root}/{idx}"
        self.root = root
        self.id = idx

    def run(self):
        kc = KazooClient()
        kc.start()

        val = b"commit" if random() > 0.5 else b"abort"
        print(f"Client {self.id} request {val.decode()}")
        kc.create(self.url, val, ephemeral=True)

        @kc.DataWatch(self.url)
        def watch(data, stat):
            if stat.version > 0:
                print(f"Client {self.id} do {data.decode()}")

        sleep(5)
        kc.stop()
        kc.close()


class Coordinator():
    def main(self):
        coord = KazooClient()
        coord.start()

        if coord.exists("/coord"):
            coord.delete("/coord", recursive=True)

        coord.create("/coord")
        coord.create("/coord/crd")
        n_clients = 5

        def solution():
            clients = coord.get_children("/coord/crd")
            commits, aborts = 0, 0
            for clt in clients:
                commits += int(coord.get(f"/coord/crd/{clt}")[0] == b"commit")
                aborts += int(coord.get(f"/coord/crd/{clt}")[0] == b"abort")

            for clt in clients:
                coord.set(f"/coord/crd/{clt}", b"commit" if commits > aborts else b"abort")

        @coord.ChildrenWatch("/coord/crd")
        def check_clients(clients):
            if len(clients) < n_clients:
                print(f"Waiting others clients: {clients}")
            elif len(clients) == n_clients:
                print("Check clients")
                solution()

        for x in range(5):
            begin = Client("/coord/crd", x)
            begin.start()


if __name__ == "__main__":
    Coordinator().main()

