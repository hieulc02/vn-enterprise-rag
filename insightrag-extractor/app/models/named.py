from models.identified import Identified


class Named(Identified):
    """A protocol for an item with a name/title"""

    title: str | None
    """The name/title of the item"""
